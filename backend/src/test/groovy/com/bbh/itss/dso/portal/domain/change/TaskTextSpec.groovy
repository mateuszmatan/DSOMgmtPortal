package com.bbh.itss.dso.portal.domain.change

import com.bbh.itss.dso.portal.domain.shared.ValidationProblems
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.change.TaskState.CANCELED
import static com.bbh.itss.dso.portal.domain.change.TaskState.CLOSED
import static com.bbh.itss.dso.portal.domain.change.TaskState.OPEN
import static com.bbh.itss.dso.portal.domain.change.TaskState.WORK_IN_PROGRESS
import static com.bbh.itss.dso.portal.domain.change.TaskText.suggestedTasks
import static com.bbh.itss.dso.portal.domain.change.TaskText.validateTasks
import static com.bbh.itss.dso.portal.domain.shared.Text.bytes

class TaskTextSpec extends Specification {

    def "change tasks trim their texts and a task without a state is open"() {
        expect:
        new TaskText(' Deploy ', ' It. ') == new TaskText('Deploy', 'It.')
        new TaskText(' ', null) == new TaskText(null, null)
        new ChangeTask(' CTASK1 ', ' Deploy ', ' It. ', null) == new ChangeTask('CTASK1', 'Deploy', 'It.', OPEN)
        ChangeTask.of(new TaskText('Deploy', 'It.')) == new ChangeTask(null, 'Deploy', 'It.', OPEN)
        new ChangeTask('CTASK1', 'Deploy', 'It.', CLOSED).text() == new TaskText('Deploy', 'It.')
        [OPEN, WORK_IN_PROGRESS, CLOSED, CANCELED].collect { it.frozen() } == [false, false, true, true]
    }

    def "a product without a template is suggested to deploy and then validate its release, cut to what ProTech takes"() {
        when:
        def suggested = suggestedTasks('CertScanner')
        def cut = suggestedTasks('é' * 150)

        then:
        suggested == [new TaskText('Deploy CertScanner to production', 'Deploy the release of CertScanner in the'
                + ' change window with its deployment jobs, then run the smoke tests of the DevSecOps pipeline and'
                + ' record the result in this task.'),
                      new TaskText('Validate CertScanner in production', 'Run the post-install validation of'
                              + ' CertScanner: the smoke tests and the monitoring, then confirm the release with the'
                              + ' business owner in this task.')]
        cut.every { bytes(it.shortDescription()) <= 160 && it.shortDescription().endsWith('...') }
    }

    def "the change tasks are refused when #problem"() {
        given:
        def problems = new ValidationProblems()

        when:
        validateTasks(tasks, problems)

        then:
        problems.list().collect { it.field() + ': ' + it.message() } == expected

        where:
        problem               | tasks                                                       || expected
        'there are none'      | []                                                          || ['tasks: add at least one change task']
        'there are 51'        | (1..51).collect { new TaskText("T$it", 'Text.') }           || ['tasks: may list at most 50 change tasks']
        'a text is missing'   | [new TaskText('One', 'First.'), new TaskText(' ', null)]    || ['tasks[1].shortDescription: is required', 'tasks[1].description: is required']
        'a text is too long'  | [new TaskText('é' * 81, 'x' * 4001)]                        || ['tasks[0].shortDescription: is too long: it may take at most 160 bytes', 'tasks[0].description: is too long: it may take at most 4000 bytes']
        'nothing, they fit'   | [new TaskText('é' * 80, 'x' * 4000)]                        || []
    }
}
