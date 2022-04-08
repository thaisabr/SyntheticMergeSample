package gitManager

import groovy.util.logging.Slf4j

@Slf4j
class GitManager {

    String localPath
    String buggyMutantFolder
    String fixedBranchName
    String buggyMutantBranchName

    GitManager(String localPath, String buggyMutantFolder){
        this.localPath = localPath
        this.buggyMutantFolder = buggyMutantFolder
        this.buggyMutantBranchName = "mutantSpg2022"
    }

    def run(){
        log.info "localpath: ${localPath}"
        fixedBranchName = verifyCurrentBranch()
        log.info "fixed branch name: ${fixedBranchName}"

        createMutantBranch()
        log.info "Mutant branch was created and checked out"
        def currentBranch = verifyCurrentBranch()
        log.info "current branch: ${currentBranch}"

        copyMutant()
        log.info "Mutant branch was changed"

        versioningMutant()
        log.info "Changes were versioned"

        commitMutant()
        log.info "Changes were commited"

        checkoutFixedBranch()
        currentBranch = verifyCurrentBranch()
        log.info "current branch: ${currentBranch}"

        merge()
        log.info "We merged branches ${fixedBranchName} and ${buggyMutantBranchName}"

        currentBranch = verifyCurrentBranch()
        log.info "current branch: ${currentBranch}"

        deleteBuggyBranch()
        log.info "Mutant branch was deleted"
    }

    def verifyCurrentBranch(){
        def builder = new ProcessBuilder('git','status')
        builder.directory(new File(localPath))
        def process = builder.start()
        process.waitFor()
        def currentBranch = process.inputStream.readLines()?.first()?.split(" ")?.last()
        process.inputStream.close()
        currentBranch
    }

    def createMutantBranch(){
        def builder = new ProcessBuilder('git','checkout', '-b', buggyMutantBranchName)
        builder.directory(new File(localPath))
        //builder.inheritIO()
        def process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "status creating mutant branch: $status"
    }

    private int copyMutantVersion1(){
        ProcessBuilder builder = new ProcessBuilder("cp", "-r", "${buggyMutantFolder}${File.separator}.",
                "src${File.separator}main${File.separator}java")
        builder.directory(new File(localPath))
        builder.inheritIO()
        Process process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "status coying mutant (version 1): $status"
        status
    }

    private int copyMutantVersion2(){
        ProcessBuilder builder = new ProcessBuilder("cp", "-r", "${buggyMutantFolder}${File.separator}.", "source")
        builder.directory(new File(localPath))
        builder.inheritIO()
        Process process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "status coying mutant (version 2): $status"
        status
    }

    def copyMutant(){
        def status = copyMutantVersion1()
        if (status!=0){
            copyMutantVersion2()
        }
    }

    def versioningMutant(){
        ProcessBuilder builder = new ProcessBuilder("git", "add", ".")
        builder.directory(new File(localPath))
        Process process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "status versioning mutant: $status"
    }

    def checkoutFixedBranch(){
        def builder = new ProcessBuilder('git','checkout', fixedBranchName)
        builder.directory(new File(localPath))
        def process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "status checking out fixed branch: $status"
    }

    def commitMutant(){
        def builder = new ProcessBuilder('git','commit','-m','buggy mutant')
        builder.directory(new File(localPath))
        builder.inheritIO()
        def process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "status commiting mutant: $status"
    }

    def merge(){
        def builder = new ProcessBuilder('git','merge', buggyMutantBranchName)
        builder.directory(new File(localPath))
        def process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "status merging branches: $status"
    }

    def deleteBuggyBranch(){
        ProcessBuilder builder = new ProcessBuilder("git", "branch", "-D", buggyMutantBranchName)
        builder.directory(new File(localPath))
        Process process = builder.start()
        def status = process.waitFor()
        process.inputStream.readLines()
        process.inputStream.close()
        log.info "status deleting buggy branch: $status"
    }

}
