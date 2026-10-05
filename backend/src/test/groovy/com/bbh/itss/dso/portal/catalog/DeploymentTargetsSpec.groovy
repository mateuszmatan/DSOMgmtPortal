package com.bbh.itss.dso.portal.catalog

import com.bbh.itss.dso.portal.domain.catalog.Region
import com.bbh.itss.dso.portal.domain.shared.ConfigTree
import jakarta.validation.Validation
import jakarta.validation.Validator
import spock.lang.Shared
import spock.lang.Specification

class DeploymentTargetsSpec extends Specification {

    static final UrbanCodeComponent GUI_COMPONENT = new UrbanCodeComponent('cert-gui', 'build/libs', '*.war', '*-plain.war',
            'gui-', '1.0.0', false)

    @Shared
    Validator validator = Validation.buildDefaultValidatorFactory().validator

    def "regions are written in lower case"() {
        expect:
        Region.values()*.configKey() == ['rd', 'qc']
    }

    def "an SSH target trims its values and writes the ones that are set in the library's order"() {
        when:
        def target = new SshTarget(' rdltaapps1.testbbh.com ', ' dsoadm ', ' /opt/cert ', ' deploy.sh ', ' version.txt ')

        then:
        target.toConfig() == [host        : 'rdltaapps1.testbbh.com', user: 'dsoadm', deployDir: '/opt/cert',
                              deployScript: 'deploy.sh', versionFile: 'version.txt']
        target.toConfig().keySet() as List == ['host', 'user', 'deployDir', 'deployScript', 'versionFile']
        !target.isEmpty()
        new SshTarget(null, null, '/opt/cert', null, null).toConfig() == [deployDir: '/opt/cert']
    }

    def "an SSH target of blank values is empty"() {
        when:
        def target = new SshTarget(' ', '', null, ' ', null)

        then:
        target == new SshTarget(null, null, null, null, null)
        target.isEmpty()
        target.toConfig() == [:]
    }

    def "OpenShift field #field is written as #config"() {
        expect:
        openShift((field): value).toConfig() == config
        !openShift((field): value).isEmpty()

        where:
        field                         | value                        || config
        'projectBuild'                | 'cert-build'                 || [projectBuildR: 'cert-build']
        'buildConfigPath'             | 'openshift/bc.yaml'          || [buildConfigPath: 'openshift/bc.yaml']
        'dockerFilePath'              | 'Dockerfile'                 || [dockerFilePath: 'Dockerfile']
        'buildContext'                | 'gui'                        || [buildContext: 'gui']
        'addFile'                     | 'target/cert.jar'            || [addFile: 'target/cert.jar']
        'dockerRepoPush'              | 'nexus-push.bbh.com/cert'    || [qcDockerRepoPush: 'nexus-push.bbh.com/cert']
        'dockerRepoPull'              | 'nexus-pull.bbh.com/cert'    || [qcDockerRepoPull: 'nexus-pull.bbh.com/cert']
        'certDir'                     | '/etc/pki/openshift'         || [openshiftCertDir: '/etc/pki/openshift']
        'nexusAuthFile'               | '/home/jenkins/auth.json'    || [nexus: [authfile: '/home/jenkins/auth.json']]
        'projectDeployment'           | 'cert-rd'                    || [projectDeploymentR: 'cert-rd']
        'deployConfigPath'            | 'openshift/dc.yaml'          || [deployConfigPath: 'openshift/dc.yaml']
        'configPath'                  | 'openshift/config'           || [configPathR: 'openshift/config']
        'skipConfigDeploy'            | true                         || [skipConfigDeploy: true]
        'healthCheckUrl'              | '/actuator/health'           || [healthCheckUrl: '/actuator/health']
        'routeHostname'               | 'cert-rd.apps.bbh.com'       || [routeHostnameR: 'cert-rd.apps.bbh.com']
        'deploymentPath'              | 'k8s/rd'                     || [deploymentPath: 'k8s/rd']
        'deploymentRepoUrl'           | 'git@bitbucket:ta/deploy.git' || [deploymentRepo: [url: 'git@bitbucket:ta/deploy.git']]
        'deploymentRepoBranch'        | 'main'                       || [deploymentRepo: [branch: 'main']]
        'deploymentRepoCredentialsId' | 'bb-creds'                   || [deploymentRepo: [credentials: 'bb-creds']]
    }

    def "a full OpenShift target writes every key in the library's order"() {
        when:
        def config = new OpenShiftTarget(' cert-build ', 'bc.yaml', 'Dockerfile', '.', 'cert.jar', 'push/cert', 'pull/cert',
                '/certs', 'auth.json', 'cert-rd', 'dc.yaml', 'config', true, '/health', 'cert.apps', 'k8s', 'ssh://git@b/d.git',
                'main', 'bb-creds').toConfig()

        then:
        config == [projectBuildR     : 'cert-build', buildConfigPath: 'bc.yaml', dockerFilePath: 'Dockerfile', buildContext: '.',
                   addFile           : 'cert.jar', qcDockerRepoPush: 'push/cert', qcDockerRepoPull: 'pull/cert',
                   openshiftCertDir  : '/certs', nexus: [authfile: 'auth.json'], projectDeploymentR: 'cert-rd',
                   deployConfigPath  : 'dc.yaml', configPathR: 'config', healthCheckUrl: '/health', routeHostnameR: 'cert.apps',
                   deploymentPath    : 'k8s', deploymentRepo: [url: 'ssh://git@b/d.git', branch: 'main', credentials: 'bb-creds'],
                   skipConfigDeploy  : true]
        config.keySet() as List == ['projectBuildR', 'buildConfigPath', 'dockerFilePath', 'buildContext', 'addFile',
                                    'qcDockerRepoPush', 'qcDockerRepoPull', 'openshiftCertDir', 'nexus', 'projectDeploymentR',
                                    'deployConfigPath', 'configPathR', 'healthCheckUrl', 'routeHostnameR', 'deploymentPath',
                                    'deploymentRepo', 'skipConfigDeploy']
    }

    def "an OpenShift target of blank values that deploys its configuration is empty"() {
        when:
        def target = new OpenShiftTarget(' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', null, ' ', ' ', ' ', ' ',
                ' ', ' ')

        then:
        target == openShift([:])
        !target.skipConfigDeploy()
        target.projectBuild() == null
        target.deploymentRepoCredentialsId() == null
        target.isEmpty()
        target.toConfig() == [:]
        openShift(skipConfigDeploy: false).isEmpty()
    }

    def "UrbanCode settings default to deploying a snapshot of only the deployed versions and waiting for it"() {
        when:
        def defaults = new UrbanCodeSettings(' ', ' ', null, null, null, null, null, ' ', ' ')

        then:
        defaults == UrbanCodeSettings.DEFAULTS
        defaults.siteName() == null
        defaults.deployProcess() == null
        !defaults.skipWait()
        defaults.deployWithSnapshot()
        !defaults.updateSnapshotComponents()
        defaults.includeOnlyDeployVersions()
        !defaults.deployOnlyChanged()
        defaults.deployDescription() == null
        defaults.requestProperties() == null
    }

    def "UrbanCode settings write nothing without applications"() {
        expect:
        written { UrbanCodeSettings.DEFAULTS.writeTo(it, []) } == [:]
        written { fullUrbanCode().writeTo(it, []) } == [:]
    }

    def "UrbanCode settings with applications write deploy.vm.dod with the library's defaults"() {
        given:
        def app = new UrbanCodeApplicationSettings('Cert', null, [], null, [])

        expect:
        written { UrbanCodeSettings.DEFAULTS.writeTo(it, [app]) } ==
                [deploy: [vm: [dod: [skipWait                 : false, deployWithSnapshot: true, updateSnapshotComp: false,
                                     includeOnlyDeployVersions: true, deployOnlyChanged: false,
                                     applications             : [[applicationName: 'Cert']]]]]]
    }

    def "every UrbanCode option is written with the applications in their order"() {
        given:
        def first = new UrbanCodeApplicationSettings('Cert', 1, ['RD'], null, [GUI_COMPONENT])
        def second = new UrbanCodeApplicationSettings('Cert Batch', 2, [], 'batch-snap', [])

        when:
        def dod = written { fullUrbanCode().writeTo(it, [first, second]) }.deploy.vm.dod

        then:
        dod == [siteName                 : 'BBH-RD', deployProcess: 'Deploy Cert', skipWait: true, deployWithSnapshot: false,
                updateSnapshotComp       : true, includeOnlyDeployVersions: false, deployOnlyChanged: true,
                deployDescription        : 'Deployed by Jenkins', requestProperties: 'key=value',
                applications             : [first.toConfig(), second.toConfig()]]
        dod.applications*.applicationName == ['Cert', 'Cert Batch']
    }

    def "an UrbanCode application trims its values and keeps each environment once"() {
        given:
        def components = [GUI_COMPONENT]

        when:
        def app = new UrbanCodeApplicationSettings(' Cert ', 3, [' RD ', 'RD', '', 'QC'], ' ', components)
        components << new UrbanCodeComponent('other', null, null, null, null, null, null)

        then:
        app.applicationName() == 'Cert'
        app.order() == 3
        app.environments() == ['RD', 'QC']
        app.snapshotName() == null
        app.components() == [GUI_COMPONENT]
        new UrbanCodeApplicationSettings(null, null, null, null, null).applicationName() == null
        new UrbanCodeApplicationSettings(null, null, null, null, null).components() == []
        new UrbanCodeApplicationSettings(null, null, null, null, null).environments() == []
    }

    def "an UrbanCode application is written with its components"() {
        when:
        def entry = new UrbanCodeApplicationSettings('Cert', 2, ['RD', 'RD2'], 'cert-snap', [GUI_COMPONENT]).toConfig()

        then:
        entry == [applicationName: 'Cert', order: 2, environments: ['RD', 'RD2'], snapshotName: 'cert-snap',
                  components     : [[componentName      : 'cert-gui', baseDir: 'build/libs', fileIncludePatterns: '*.war',
                                     fileExcludePatterns: '*-plain.war', versionPrefix: 'gui-', version: '1.0.0',
                                     incrementalVersion : false]]]
        entry.keySet() as List == ['applicationName', 'order', 'environments', 'snapshotName', 'components']
        new UrbanCodeApplicationSettings('Cert', null, [], null, []).toConfig() == [applicationName: 'Cert']
    }

    def "an UrbanCode component trims its values and publishes incremental versions by default"() {
        when:
        def component = new UrbanCodeComponent(' cert-gui ', ' ', ' ', ' ', ' ', ' ', null)

        then:
        component.componentName() == 'cert-gui'
        component.baseDir() == null
        component.fileIncludePatterns() == null
        component.fileExcludePatterns() == null
        component.versionPrefix() == null
        component.version() == null
        component.incrementalVersion()
        component.toConfig() == [componentName: 'cert-gui', incrementalVersion: true]
        component.toConfig().keySet() as List == ['componentName', 'incrementalVersion']
        new UrbanCodeComponent(null, null, null, null, null, null, false).componentName() == null
        !new UrbanCodeComponent(null, null, null, null, null, null, false).incrementalVersion()
        GUI_COMPONENT.toConfig().keySet() as List == ['componentName', 'baseDir', 'fileIncludePatterns',
                                                       'fileExcludePatterns', 'versionPrefix', 'version', 'incrementalVersion']
    }

    def "a stored UrbanCode application gives back the settings it was created from"() {
        given:
        def settings = new UrbanCodeApplicationSettings('Cert', 2, ['RD', 'QC'], 'cert-snap',
                [GUI_COMPONENT, new UrbanCodeComponent('cert-api', null, null, null, null, null, true)])

        when:
        def stored = new UrbanCodeApplication(null, 4, settings)

        then:
        stored.settings() == settings
        stored.position == 4
        new UrbanCodeApplication(null, 0, new UrbanCodeApplicationSettings('Cert', null, [], null, [])).settings() ==
                new UrbanCodeApplicationSettings('Cert', null, [], null, [])
    }

    def "an UrbanCode application loaded by JPA starts without components"() {
        expect:
        new UrbanCodeApplication().settings() == new UrbanCodeApplicationSettings(null, null, [], null, [])
    }

    def "valid deployment targets pass bean validation"() {
        expect:
        validator.validate(section).isEmpty()

        where:
        section << [new SshTarget('rdltaapps1.testbbh.com', 'dsoadm', '/opt', 'deploy.sh', 'version'),
                    openShift(projectBuild: 'cert', deploymentRepoUrl: 'https://bitbucket/ta/deploy.git'),
                    openShift(deploymentRepoUrl: 'ssh://git@bitbucket:7999/ta/deploy.git'),
                    openShift(deploymentRepoUrl: 'git@bitbucket:ta/deploy.git'),
                    fullUrbanCode(),
                    new UrbanCodeApplicationSettings('Cert', 999, ['RD', 'qc_2-a'], 'snap', [GUI_COMPONENT]),
                    GUI_COMPONENT]
    }

    def "bean validation rejects #description"() {
        expect:
        validator.validate(section)*.propertyPath*.toString() == [property]

        where:
        description                        | section                                                                       || property
        'an SSH host with a space'         | new SshTarget('rd host', null, null, null, null)                              || 'host'
        'a too long SSH user'              | new SshTarget(null, 'u' * 101, null, null, null)                              || 'user'
        'a too long version file'          | new SshTarget(null, null, null, null, 'v' * 501)                              || 'versionFile'
        'a deployment repository w/o URL'  | openShift(deploymentRepoUrl: 'bitbucket/ta/deploy')                           || 'deploymentRepoUrl'
        'a too long OpenShift project'     | openShift(projectBuild: 'p' * 201)                                            || 'projectBuild'
        'a too long route host name'       | openShift(routeHostname: 'r' * 301)                                           || 'routeHostname'
        'a too long UrbanCode site'        | new UrbanCodeSettings('s' * 201, null, null, null, null, null, null, null, null) || 'siteName'
        'too long request properties'      | new UrbanCodeSettings(null, null, null, null, null, null, null, null, 'p' * 2001) || 'requestProperties'
        'an application without a name'    | new UrbanCodeApplicationSettings(' ', null, [], null, [])                     || 'applicationName'
        'an application ordered 0'         | new UrbanCodeApplicationSettings('Cert', 0, [], null, [])                     || 'order'
        'an application ordered 1000'      | new UrbanCodeApplicationSettings('Cert', 1000, [], null, [])                  || 'order'
        'an environment with a space'      | new UrbanCodeApplicationSettings('Cert', null, ['R D'], null, [])             || 'environments[0].<list element>'
        'more than 20 environments'        | new UrbanCodeApplicationSettings('Cert', null, (1..21).collect { "E$it" as String }, null, []) || 'environments'
        'a too long snapshot name'         | new UrbanCodeApplicationSettings('Cert', null, [], 's' * 201, [])             || 'snapshotName'
        'a component without a name'       | new UrbanCodeApplicationSettings('Cert', null, [], null, [component(' ')])    || 'components[0].componentName'
        'more than 50 components'          | new UrbanCodeApplicationSettings('Cert', null, [], null, (1..51).collect { component("c$it") }) || 'components'
        'a missing component name'         | component(null)                                                               || 'componentName'
        'a too long base directory'        | new UrbanCodeComponent('c', 'b' * 501, null, null, null, null, null)          || 'baseDir'
    }

    private static UrbanCodeSettings fullUrbanCode() {
        new UrbanCodeSettings(' BBH-RD ', ' Deploy Cert ', true, false, true, false, true, ' Deployed by Jenkins ', ' key=value ')
    }

    private static UrbanCodeComponent component(String name) {
        new UrbanCodeComponent(name, null, null, null, null, null, null)
    }

    private static OpenShiftTarget openShift(Map args) {
        new OpenShiftTarget(args.projectBuild as String, args.buildConfigPath as String, args.dockerFilePath as String,
                args.buildContext as String, args.addFile as String, args.dockerRepoPush as String,
                args.dockerRepoPull as String, args.certDir as String, args.nexusAuthFile as String,
                args.projectDeployment as String, args.deployConfigPath as String, args.configPath as String,
                args.skipConfigDeploy as Boolean, args.healthCheckUrl as String, args.routeHostname as String,
                args.deploymentPath as String, args.deploymentRepoUrl as String, args.deploymentRepoBranch as String,
                args.deploymentRepoCredentialsId as String)
    }

    private static Map written(Closure write) {
        def tree = new ConfigTree()
        write(tree)
        tree.toMap()
    }
}
