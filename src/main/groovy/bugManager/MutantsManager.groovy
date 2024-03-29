package bugManager

import groovy.util.logging.Slf4j

import java.util.concurrent.TimeUnit

@Slf4j
class MutantsManager {

    Bug bug
    String mutantsFolder
    List<String> methodsToMutate
    List<String> mutantsName
    String defects4jFolder
    String instrumentedClassesFile

    MutantsManager(Bug bug, def methodsToMutate, String defects4jFolder){
        this.bug = bug
        this.mutantsFolder = "${bug.buggyFolder}${File.separator}mutants"
        this.methodsToMutate = methodsToMutate
        this.mutantsName = []
        this.defects4jFolder = defects4jFolder
        this.instrumentedClassesFile = "${bug.buggyFolder}${File.separator}instrument_classes"
    }

    void generateInstrumentedClassesFile(){
        def newFile = new File(instrumentedClassesFile)
        newFile.createNewFile()
        log.info "Methods to mutate: ${methodsToMutate.size()}"

        newFile.withWriter("utf-8") { out ->
            methodsToMutate.eachWithIndex{ method, index ->
                if( (index+1) == methodsToMutate.size()) out.write(method)
                else out.write(method+"\n")
            }
        }
    }

    void generateMutants(){
        ProcessBuilder builder = new ProcessBuilder("defects4j", "mutation", "MAJOR-OPT", "-i", "instrument_classes")
        builder.directory(new File(bug.buggyFolder))
        Process process = builder.start()
        //builder.inheritIO()
        def status = process.waitFor(3, TimeUnit.MINUTES)
        if(!status){
            process.destroyForcibly()
            log.info "Status when generating mutants: The timeout was reached and the process was killed"
        } else {
            process.inputStream.eachLine { log.info it.toString() }
            process.inputStream.close()
            log.info "Status when generating mutants: $status"
        }
    }

    boolean run(){
        if(methodsToMutate.isEmpty()){
            log.info "There is no methods to mutate."
            return false
        } else {
            generateInstrumentedClassesFile()
            log.info "The instrumented classes file was created: ${instrumentedClassesFile}"
            generateMutants()
            configureMutantsName()
            log.info "Defects4J's generated ${mutantsName.size()} mutants in folder: ${mutantsFolder}"
            return true
        }
    }

    void configureMutantsName(){
        def file = new File(mutantsFolder)
        mutantsName = file.listFiles().findAll{ it.isDirectory() }?.collect{it.name}?.sort{
            it as Integer
        }

    }

}
