package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.application.dsoconfig.port.out.PublicationLockPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.locks.ReentrantLock;

@Component
class PublicationLockAdapter implements PublicationLockPort {

    private final ReentrantLock queue = new ReentrantLock(true);
    private final GlobalSettingsJpaRepository settings;

    PublicationLockAdapter(GlobalSettingsJpaRepository settings) {
        this.settings = settings;
    }

    @Override
    public void lock() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("The pipeline configurations can only be locked inside a transaction");
        }
        if (!queue.isHeldByCurrentThread()) {
            TransactionSynchronizationManager.registerSynchronization(new ReleaseAfterCompletion());
            queue.lock();
        }
        settings.findForUpdate(GlobalSettingsEntity.ID);
    }

    private final class ReleaseAfterCompletion implements TransactionSynchronization {

        @Override
        public void afterCompletion(int status) {
            if (queue.isHeldByCurrentThread()) {
                queue.unlock();
            }
        }
    }
}
