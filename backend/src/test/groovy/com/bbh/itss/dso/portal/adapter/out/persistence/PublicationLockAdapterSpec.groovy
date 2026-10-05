package com.bbh.itss.dso.portal.adapter.out.persistence

import com.bbh.itss.dso.portal.domain.settings.GlobalSettings
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import spock.lang.Specification
import spock.lang.Subject

import javax.sql.DataSource
import java.sql.SQLException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:publication-lock;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa'])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import([PublicationLockAdapter, GlobalSettingsPersistenceAdapter])
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PublicationLockAdapterSpec extends Specification {

    @Subject
    @Autowired
    PublicationLockAdapter lock

    @Autowired
    GlobalSettingsPersistenceAdapter settings

    @Autowired
    JdbcTemplate jdbc

    @Autowired
    DataSource dataSource

    @Autowired
    PlatformTransactionManager transactionManager

    TransactionTemplate transactions

    def setup() {
        transactions = new TransactionTemplate(transactionManager)
        transactions.executeWithoutResult { settings.save(GlobalSettings.bbhDefaults()) }
    }

    def cleanup() {
        jdbc.update('DELETE FROM DSO_GLOBAL_SEVERITY_LIMIT')
        jdbc.update('DELETE FROM DSO_GLOBAL_SETTINGS')
    }

    def "the configurations cannot be locked outside a transaction"() {
        when:
        lock.lock()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The pipeline configurations can only be locked inside a transaction'
    }

    def "the lock keeps every other connection from changing the global settings until the transaction ends"() {
        when:
        boolean changedWhileLocked = transactions.execute {
            lock.lock()
            changeFromAnotherConnection()
        }

        then:
        !changedWhileLocked
        changeFromAnotherConnection()
    }

    def "a second transaction gets the lock only when the first one has ended, also after a rollback (#outcome)"() {
        given:
        def locked = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def events = [].asSynchronized()

        when:
        def first = CompletableFuture.runAsync {
            try {
                transactions.executeWithoutResult {
                    lock.lock()
                    lock.lock()
                    locked.countDown()
                    release.await(5, TimeUnit.SECONDS)
                    events << 'first ends'
                    if (failing) {
                        throw new IllegalArgumentException('broken change')
                    }
                }
            } catch (IllegalArgumentException ignored) {
                events << 'first rolled back'
            }
        }
        locked.await(5, TimeUnit.SECONDS)
        def second = CompletableFuture.runAsync {
            transactions.executeWithoutResult {
                lock.lock()
                events << 'second locked'
            }
        }
        Thread.sleep(300)
        events << 'released'
        release.countDown()
        first.get(5, TimeUnit.SECONDS)
        second.get(5, TimeUnit.SECONDS)

        then:
        events == expected

        where:
        outcome       | failing || expected
        'committed'   | false   || ['released', 'first ends', 'second locked']
        'rolled back' | true    || ['released', 'first ends', 'first rolled back', 'second locked']
    }

    private boolean changeFromAnotherConnection() {
        def connection = dataSource.connection
        try {
            connection.autoCommit = true
            connection.createStatement().execute('SET LOCK_TIMEOUT 200')
            connection.createStatement().executeUpdate('UPDATE DSO_GLOBAL_SETTINGS SET VERSION = VERSION WHERE ID = 1') == 1
        } catch (SQLException ignored) {
            false
        } finally {
            connection.close()
        }
    }
}
