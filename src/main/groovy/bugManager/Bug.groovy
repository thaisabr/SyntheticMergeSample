package bugManager

import groovy.util.logging.Slf4j

import java.util.concurrent.TimeUnit

@Slf4j
class Bug {

    String id
    String project
    List<String> failingTests
    List<String> modifiedClasses
    String buggyFolder
    String fixedFolder
    String buggyRevision
    String fixedRevision

    void executeTestWithCoverage(String test){
        log.info "Executing test with coverage: ${buggyFolder}, ${test}"
        ProcessBuilder builder = new ProcessBuilder("defects4j", "coverage", "-t", test)
        builder.directory(new File(buggyFolder))
        Process process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "Status executing test with coverage: $status"
    }

    def compile(){
        ProcessBuilder builder = new ProcessBuilder("defects4j", "compile")
        builder.directory(new File(buggyFolder))
        Process process = builder.start()
        def status = process.waitFor(5, TimeUnit.MINUTES)
        log.info "status waitfor timeout 5: $status"
        if(status){
            process.inputStream.eachLine { log.info it.toString() }
            process.inputStream.close()
            log.info "Status compiling project: $status"
        } else {
            process.destroyForcibly()
            process.waitFor()
            throw new Exception("Problem to compile project.")
        }
        status
    }

    boolean executeTest(String test, String currentFolder) throws Exception {
        boolean testPassed
        def status = compile()

        if(status != 0) throw new Exception("Error while compiling project.")

        ProcessBuilder builder = new ProcessBuilder("defects4j", "test", "-t", test)
        builder.directory(new File(currentFolder))
        Process process = builder.start()
        builder.inheritIO()
        status = process.waitFor(5, TimeUnit.MINUTES)
        log.info "status waitfor timeout 5: $status"
        if(status){
            def output = process.inputStream.readLines()
            output.each { log.info it.toString() }
            String failingTest = output.find{
                it ==~ /Failing tests: [1-9]+/
            }
            process.inputStream.close()
            log.info "Status executing test: $status"
            testPassed = (failingTest == null) //teste passou
        } else {
            process.destroyForcibly()
            process.waitFor()
            throw new Exception("Problem to execute test '${test}' in folder '${currentFolder}'.")
        }

        return testPassed
    }

}
