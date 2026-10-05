package com.bbh.itss.dso.portal.adapter.in.web

import jakarta.validation.Validation
import jakarta.validation.Validator
import spock.lang.Shared
import spock.lang.Specification

class DeploymentTargetDtosSpec extends Specification {

    static final UrbanCodeComponentDto GUI_COMPONENT = new UrbanCodeComponentDto('cert-gui', 'build/libs', '*.war',
            '*-plain.war', 'gui-', '1.0.0', false)

    @Shared
    Validator validator = Validation.buildDefaultValidatorFactory().validator

    def "deployment target requests are normalised before they are validated"() {
        expect:
        new SshTargetDto(' rd.host ', ' ', ' /opt ', ' ', ' ') == new SshTargetDto('rd.host', null, '/opt', null, null)
        openShift(projectBuild: ' cert ', skipConfigDeploy: null) == openShift(projectBuild: 'cert', skipConfigDeploy: false)
        new UrbanCodeSettingsDto(' BBH-RD ', ' ', null, null, null, null, null, ' ', ' ') ==
                new UrbanCodeSettingsDto('BBH-RD', null, false, true, false, true, false, null, null)
        new UrbanCodeApplicationSettingsDto(' Cert ', null, [' RD ', 'RD'], ' ', null) ==
                new UrbanCodeApplicationSettingsDto('Cert', null, ['RD'], null, [])
        new UrbanCodeComponentDto(' c ', ' ', ' ', ' ', ' ', ' ', null) ==
                new UrbanCodeComponentDto('c', null, null, null, null, null, true)
    }

    def "valid deployment targets pass bean validation"() {
        expect:
        validator.validate(section).isEmpty()

        where:
        section << [new SshTargetDto('rdltaapps1.testbbh.com', 'dsoadm', '/opt', 'deploy.sh', 'version'),
                    openShift(projectBuild: 'cert', deploymentRepoUrl: 'https://bitbucket/ta/deploy.git'),
                    openShift(deploymentRepoUrl: 'ssh://git@bitbucket:7999/ta/deploy.git'),
                    openShift(deploymentRepoUrl: 'git@bitbucket:ta/deploy.git'),
                    new UrbanCodeSettingsDto(' BBH-RD ', ' Deploy Cert ', true, false, true, false, true,
                            ' Deployed by Jenkins ', ' key=value '),
                    new UrbanCodeApplicationSettingsDto('Cert', 999, ['RD', 'qc_2-a'], 'snap', [GUI_COMPONENT]),
                    GUI_COMPONENT]
    }

    def "bean validation rejects #description"() {
        expect:
        validator.validate(section)*.propertyPath*.toString() == [property]

        where:
        description                        | section                                                                          || property
        'an SSH host with a space'         | new SshTargetDto('rd host', null, null, null, null)                              || 'host'
        'a too long SSH user'              | new SshTargetDto(null, 'u' * 101, null, null, null)                              || 'user'
        'a too long version file'          | new SshTargetDto(null, null, null, null, 'v' * 501)                              || 'versionFile'
        'a deployment repository w/o URL'  | openShift(deploymentRepoUrl: 'bitbucket/ta/deploy')                              || 'deploymentRepoUrl'
        'a too long OpenShift project'     | openShift(projectBuild: 'p' * 201)                                               || 'projectBuild'
        'a too long route host name'       | openShift(routeHostname: 'r' * 301)                                              || 'routeHostname'
        'a too long UrbanCode site'        | new UrbanCodeSettingsDto('s' * 201, null, null, null, null, null, null, null, null) || 'siteName'
        'too long request properties'      | new UrbanCodeSettingsDto(null, null, null, null, null, null, null, null, 'p' * 2001) || 'requestProperties'
        'an application without a name'    | new UrbanCodeApplicationSettingsDto(' ', null, [], null, [])                     || 'applicationName'
        'an application ordered 0'         | new UrbanCodeApplicationSettingsDto('Cert', 0, [], null, [])                     || 'order'
        'an application ordered 1000'      | new UrbanCodeApplicationSettingsDto('Cert', 1000, [], null, [])                  || 'order'
        'an environment with a space'      | new UrbanCodeApplicationSettingsDto('Cert', null, ['R D'], null, [])             || 'environments[0].<list element>'
        'more than 20 environments'        | new UrbanCodeApplicationSettingsDto('Cert', null, (1..21).collect { "E$it" as String }, null, []) || 'environments'
        'a too long snapshot name'         | new UrbanCodeApplicationSettingsDto('Cert', null, [], 's' * 201, [])             || 'snapshotName'
        'a component without a name'       | new UrbanCodeApplicationSettingsDto('Cert', null, [], null, [component(' ')])    || 'components[0].componentName'
        'more than 50 components'          | new UrbanCodeApplicationSettingsDto('Cert', null, [], null, (1..51).collect { component("c$it") }) || 'components'
        'a missing component name'         | component(null)                                                                  || 'componentName'
        'a too long base directory'        | new UrbanCodeComponentDto('c', 'b' * 501, null, null, null, null, null)          || 'baseDir'
    }

    def "the SSH host and the deployment repository explain what they expect"() {
        expect:
        validator.validate(new SshTargetDto('rd host', null, null, null, null))*.message ==
                ['must be a host name such as rdltaapps1.testbbh.com']
        validator.validate(openShift(deploymentRepoUrl: 'bitbucket/ta/deploy'))*.message == ['must be a Git repository URL']
    }

    private static UrbanCodeComponentDto component(String name) {
        new UrbanCodeComponentDto(name, null, null, null, null, null, null)
    }

    private static OpenShiftTargetDto openShift(Map args) {
        new OpenShiftTargetDto(args.projectBuild as String, args.buildConfigPath as String, args.dockerFilePath as String,
                args.buildContext as String, args.addFile as String, args.dockerRepoPush as String,
                args.dockerRepoPull as String, args.certDir as String, args.nexusAuthFile as String,
                args.projectDeployment as String, args.deployConfigPath as String, args.configPath as String,
                args.skipConfigDeploy as Boolean, args.healthCheckUrl as String, args.routeHostname as String,
                args.deploymentPath as String, args.deploymentRepoUrl as String, args.deploymentRepoBranch as String,
                args.deploymentRepoCredentialsId as String)
    }
}
