package bugManager

import groovy.util.logging.Slf4j

@Slf4j
class Bug {

    String id
    String project
    List<String> failingTests
    List<String> modifiedClasses
    String buggyFolder
    String fixedFolder

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

    boolean executeTest(String test){
        ProcessBuilder builder = new ProcessBuilder("defects4j", "test", "-t", test)
        builder.directory(new File(fixedFolder))
        Process process = builder.start()
        builder.inheritIO()
        def status = process.waitFor()
        def output = process.inputStream.readLines()
        output.each { log.info it.toString() }
        String failingTest = output.find{
            it ==~ /Failing tests: [1-9]+/
        }
        process.inputStream.close()
        log.info "Status executing test: $status"
        return failingTest == null //teste passou
    }

}
