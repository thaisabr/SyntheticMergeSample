package gitManager

import groovy.util.logging.Slf4j

@Slf4j
class GitManager {

    String originalBugFolder
    String buggyMutantFolder
    String detachedName
    String originalBuggyBranchName
    String fixedBranchName
    String buggyMutantBranchName
    String fixedBugFolder

    GitManager(String originalBugFolder, String buggyMutantFolder, String fixedBugFolder){
        this.originalBugFolder = originalBugFolder
        this.buggyMutantFolder = buggyMutantFolder
        this.fixedBugFolder = fixedBugFolder
        this.buggyMutantBranchName = "mutantSpg2022"
        this.fixedBranchName = "fixedSpg2022"
        this.originalBuggyBranchName = "bugSpg2022"
    }

    def run(){
        log.info "localpath: ${originalBugFolder}"
        detachedName = verifyCurrentBranch()
        log.info "detached HEAD: ${detachedName}"

        configureOriginalBug()

        def currentBranch = configureFixBranch()

        mergeFixedBranch()
        log.info "We merged branches ${currentBranch} and ${fixedBranchName}"

        currentBranch = configureMutantBranch()

        mergeBuggyBranch()
        log.info "We merged branches ${currentBranch} and ${buggyMutantBranchName}"

        currentBranch = verifyCurrentBranch()
        log.info "current branch: ${currentBranch}"

        //deleteBuggyBranch()
        //log.info "Mutant branch was deleted"

        //deleteFixedBranch()
        //log.info "Fix branch was deleted"
    }

    private configureOriginalBug(){
        createOriginalBuggyBranch()
        def currentBranch = verifyCurrentBranch()
        log.info "current branch: ${currentBranch}"

        checkoutMasterBranch()
        currentBranch = verifyCurrentBranch()
        log.info "current branch: ${currentBranch}"

        mergeOriginalBugggyBranch()
        log.info "The original buggy branch was merged"
        currentBranch
    }

    private configureFixBranch(){
        createFixBranch()
        log.info "Fix branch was created and checked out"
        def currentBranch = verifyCurrentBranch()
        log.info "current branch: ${currentBranch}"

        copyFix()
        log.info "Fix branch was changed"

        versioningSource()
        log.info "Changes were versioned"

        commitFix()
        log.info "Changes were commited"

        checkoutMasterBranch()
        currentBranch = verifyCurrentBranch()
        log.info "current branch: ${currentBranch}"
        currentBranch
    }

    private configureMutantBranch(){
        createMutantBranch()
        log.info "Mutant branch was created and checked out"
        def currentBranch = verifyCurrentBranch()
        log.info "current branch: ${currentBranch}"

        copyMutant()
        log.info "Mutant branch was changed"

        versioningSource()
        log.info "Changes were versioned"

        commitMutant()
        log.info "Changes were commited"

        checkoutMasterBranch()
        currentBranch = verifyCurrentBranch()
        log.info "current branch: ${currentBranch}"
        currentBranch
    }

    def createOriginalBuggyBranch(){
        def builder = new ProcessBuilder('git','branch', originalBuggyBranchName)
        builder.directory(new File(originalBugFolder))
        def process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "status creating original buggy branch: $status"
    }

    def mergeOriginalBugggyBranch(){
        def builder = new ProcessBuilder('git','merge', originalBuggyBranchName)
        builder.directory(new File(originalBugFolder))
        def process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "status merging original buggy branch: $status"
    }

    def checkoutMasterBranch(){
        def builder = new ProcessBuilder('git','checkout', '-f', 'master')
        builder.directory(new File(originalBugFolder))
        def process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "status checked out master branch: $status"
    }

    def mergeFixedBranch(){
        def builder = new ProcessBuilder('git','merge', fixedBranchName)
        builder.directory(new File(originalBugFolder))
        builder.inheritIO()
        def process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "status merging fix branch: $status"
    }

    def verifyCurrentBranch(){
        def builder = new ProcessBuilder('git','status')
        builder.directory(new File(originalBugFolder))
        def process = builder.start()
        process.waitFor()
        def currentBranch = process.inputStream.readLines()?.first()?.split(" ")?.last()
        process.inputStream.close()
        currentBranch
    }

    def createMutantBranch(){
        def builder = new ProcessBuilder('git','checkout', '-b', buggyMutantBranchName)
        builder.directory(new File(originalBugFolder))
        def process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "status creating mutant branch: $status"
    }

    private int copyMutantVersion1(){
        ProcessBuilder builder = new ProcessBuilder("cp", "-r", "${buggyMutantFolder}${File.separator}.",
                "src${File.separator}main${File.separator}java")
        builder.directory(new File(originalBugFolder))
        builder.inheritIO()
        Process process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "status copying mutant (version 1): $status"
        status
    }

    private int copyMutantVersion2(){
        ProcessBuilder builder = new ProcessBuilder("cp", "-r", "${buggyMutantFolder}${File.separator}.", "source")
        builder.directory(new File(originalBugFolder))
        builder.inheritIO()
        Process process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "status copying mutant (version 2): $status"
        status
    }

    def copyMutant(){
        def status = copyMutantVersion1()
        if (status!=0){
            copyMutantVersion2()
        }
    }

    def versioningSource(){
        ProcessBuilder builder

        def source1 = "${originalBugFolder}${File.separator}source${File.separator}"
        def source2 = "${originalBugFolder}${File.separator}src${File.separator}main${File.separator}java${File.separator}"
        def file = new File(source1)

        def source
        if(file.exists()){
           source = source1
        } else {
           source = source2
        }

        builder = new ProcessBuilder("git", "add", "${source}${File.separator}.")
        builder.directory(new File(originalBugFolder))
        Process process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "status versioning: $status"
    }

    def commitMutant(){
        def builder = new ProcessBuilder('git','commit','-m','buggy mutant')
        builder.directory(new File(originalBugFolder))
        builder.inheritIO()
        def process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "status commiting mutant: $status"
    }

    def createFixBranch(){
        def builder = new ProcessBuilder('git','checkout', '-b', fixedBranchName)
        builder.directory(new File(originalBugFolder))
        def process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "status creating fixed branch: $status"
    }

    private int copyFixedVersion1(){
        def source = "${fixedBugFolder}${File.separator}src${File.separator}main${File.separator}java${File.separator}."
        def destination = "${originalBugFolder}${File.separator}src${File.separator}main${File.separator}java"

        log.info "source: $source"
        log.info "destination: $destination"

        ProcessBuilder builder = new ProcessBuilder("cp", "-r", source, destination)
        builder.directory(new File(originalBugFolder))
        builder.inheritIO()
        Process process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "status copying fix (version 1): $status"
        status
    }

    private int copyFixedVersion2(){
        def source = "${fixedBugFolder}${File.separator}source${File.separator}."
        def destination = "${originalBugFolder}${File.separator}source"

        log.info "source: $source"
        log.info "destination: $destination"

        ProcessBuilder builder = new ProcessBuilder("cp", "-r", source, destination)
        builder.directory(new File(originalBugFolder))
        builder.inheritIO()
        Process process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "status copying fix (version 2): $status"
        status
    }

    def copyFix(){
        def status = copyFixedVersion1()
        if (status!=0){
            copyFixedVersion2()
        }
    }

    def commitFix(){
        def builder = new ProcessBuilder('git','commit','-m','bug fix')
        builder.directory(new File(originalBugFolder))
        builder.inheritIO()
        def process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "status commiting bugfix: $status"
    }

    def mergeBuggyBranch(){
        def builder = new ProcessBuilder('git','merge', buggyMutantBranchName)
        builder.directory(new File(originalBugFolder))
        def process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "status merging buggy branch: $status"
    }

    def deleteBuggyBranch(){
        ProcessBuilder builder = new ProcessBuilder("git", "branch", "-D", buggyMutantBranchName)
        builder.directory(new File(originalBugFolder))
        Process process = builder.start()
        def status = process.waitFor()
        process.inputStream.readLines()
        process.inputStream.close()
        log.info "status deleting buggy branch: $status"
    }

    def deleteFixedBranch(){
        ProcessBuilder builder = new ProcessBuilder("git", "branch", "-D", fixedBranchName)
        builder.directory(new File(originalBugFolder))
        Process process = builder.start()
        def status = process.waitFor()
        process.inputStream.readLines()
        process.inputStream.close()
        log.info "status deleting fix branch: $status"
    }

}
