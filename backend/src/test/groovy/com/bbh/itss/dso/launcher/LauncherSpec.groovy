package com.bbh.itss.dso.launcher

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

import static java.nio.file.Files.createDirectory
import static java.nio.file.Files.newInputStream
import static java.nio.file.Files.newOutputStream
import static java.nio.file.Files.readAllBytes
import static java.nio.file.Files.readString
import static java.nio.file.Files.writeString
import static javax.tools.ToolProvider.getSystemJavaCompiler

class LauncherSpec extends Specification {

    static final String ECHO = '''
        import java.nio.file.*;
        public class Echo {
            public static void main(String[] args) throws Exception {
                String name = Path.of(System.getProperty("java.class.path")).getFileName().toString();
                Files.writeString(Path.of(args[0], name + ".txt"), String.join(" ", args) + " " + System.getProperty("java.home"));
                if (name.startsWith("sleeper")) {
                    Thread.sleep(60_000);
                }
                while (!Files.exists(Path.of(args[0], "sleeper.jar.txt"))) {
                    Thread.sleep(50);
                }
                System.exit(Integer.parseInt(args[1]));
            }
        }'''

    @TempDir
    Path folder

    def "the launcher packs the DevSecOps Management Portal and Beadle"() {
        expect:
        Launcher.APPLICATIONS == ['dso-portal', 'beadle']
    }

    def "every application starts in its own JVM, the one running the launcher, with the arguments passed on; the first one to end ends the others and gives the exit code"() {
        given:
        def jar = applicationJar()
        def output = createDirectory(folder.resolve('output'))
        def temporaryFoldersBefore = temporaryFolders()

        when:
        def code = Launcher.run(['echo', 'sleeper'], { newInputStream(jar) }, [output.toString(), '3', '--spring.profiles.active=rd'])

        then:
        code == 3
        readString(output.resolve('echo.jar.txt')) == "$output 3 --spring.profiles.active=rd ${System.getProperty('java.home')}"
        readString(output.resolve('sleeper.jar.txt')) == "$output 3 --spring.profiles.active=rd ${System.getProperty('java.home')}"
        temporaryFolders() == temporaryFoldersBefore
    }

    private Path applicationJar() {
        def source = folder.resolve('Echo.java')
        writeString(source, ECHO)
        assert getSystemJavaCompiler().run(null, null, null, '-d', folder.toString(), source.toString()) == 0
        def manifest = new Manifest()
        manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = '1.0'
        manifest.mainAttributes[Attributes.Name.MAIN_CLASS] = 'Echo'
        def jar = folder.resolve('application.jar')
        new JarOutputStream(newOutputStream(jar), manifest).withCloseable { out ->
            out.putNextEntry(new JarEntry('Echo.class'))
            out.write(readAllBytes(folder.resolve('Echo.class')))
            out.closeEntry()
        }
        jar
    }

    private static List<String> temporaryFolders() {
        new File(System.getProperty('java.io.tmpdir')).list().findAll { it.startsWith('bbh-devsecops-') }.sort()
    }
}
