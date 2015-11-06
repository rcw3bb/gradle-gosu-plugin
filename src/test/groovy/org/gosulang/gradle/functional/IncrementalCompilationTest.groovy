package org.gosulang.gradle.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.gradle.testkit.runner.UnexpectedBuildSuccess

class IncrementalCompilationTest extends AbstractGosuPluginSpecification {

    File srcMainGosu, A, B

    /**
     * super#setup is invoked automatically
     * @return
     */
    def setup() {
        srcMainGosu = testProjectDir.newFolder('src', 'main', 'gosu')
        A = new File(srcMainGosu, 'A.gs')
        B = new File(srcMainGosu, 'B.gs')
    }
    
    def 'A references B; will A be recompiled if it does not change, but B\'s API does?'() {
        given:
        buildScript << getBasicBuildScriptForTesting()
        
        A << """
             class A {
               static var whatIsB : String = B.abc
             }
             """
        
        B << """
             class B  {
               static property get abc() : String {
                 return "something"
               }
             }
             """
        
        when:
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath(pluginClasspath)
                .withArguments('clean', 'compileGosu', '-i')

        BuildResult result = runner.build()
        
        then:
        notThrown(UnexpectedBuildFailure)
        String buildOutput = asPath(testProjectDir.root.absolutePath, 'build', 'classes', 'main')
        new File(buildOutput).exists()
        new File(buildOutput, 'A.class').exists()
        new File(buildOutput, 'B.class').exists()
        
        println('Done with first pass')
        
        and: // modify B in a way that invalidates A
        B.setText('') // truncates the file
        B << """
             class B  {
               static property get xyz() : String { //changed the public API!
                 return "something"
               }
             }
             """
        
        when:
        println('B is now:')
        println(B.getText())
//        GradleRunner secondRunner = GradleRunner.create()
//                .withProjectDir(testProjectDir.root)
//                .withPluginClasspath(pluginClasspath)
//                .withArguments('compileGosu', '-ds')
        runner.withArguments('compileGosu', '-d') // intentionally use debug logging

//        BuildResult secondResult = secondRunner.build() //AndFail() //build should fail since A now references an invalid symbol, B.abc
        result = runner.buildAndFail()
        
        then:
        notThrown(UnexpectedBuildSuccess)
        result.standardOutput.contains('Executing task \':compileGosu\'')
        result.standardOutput.contains('/src/main/gosu/B.gs has changed.')
        !result.standardOutput.contains('[ant:gosuc] A.gs omitted as')
        !result.standardOutput.contains('[ant:gosuc] B.gs omitted as')
        result.standardOutput.contains('src/main/gosu/A.gs:[3,46] error: No static property descriptor found for property, abc, on class, Type<B>')
    }

}
