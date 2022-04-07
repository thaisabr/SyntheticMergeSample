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
        fixedBranchName = verifyCurrentBranch()
        log.info "Current branch name: ${fixedBranchName}"

        createMutantBranch()
        log.info "Mutant branch was created and checked out"

        copyMutant()
        log.info "Mutant branch was changed"

        versioningMutant()
        log.info "Changes were versioned"

        commitMutant()
        log.info "Changes were commited"

        checkoutFixedBranch()
        log.info "Current branch name: ${fixedBranchName}"

        merge()
        log.info "We merged branches ${fixedBranchName} and ${buggyMutantBranchName}"

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
        def process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "status: $status"
    }

    def copyMutant(){
        ProcessBuilder builder = new ProcessBuilder("cp", "-r", "${buggyMutantFolder}${File.separator}.",
                "src${File.separator}main${File.separator}java")
        builder.directory(new File(localPath))
        Process process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "status: $status"
    }

    def versioningMutant(){
        ProcessBuilder builder = new ProcessBuilder("git", "add", ".")
        builder.directory(new File(localPath))
        Process process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "status: $status"
    }

    def checkoutFixedBranch(){
        def builder = new ProcessBuilder('git','checkout', fixedBranchName)
        builder.directory(new File(localPath))
        def process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "status: $status"
    }

    def commitMutant(){
        def builder = new ProcessBuilder('git','commit')
        builder.directory(new File(localPath))
        def process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "status: $status"
    }

    def merge(){
        def builder = new ProcessBuilder('git','merge', buggyMutantBranchName)
        builder.directory(new File(localPath))
        def process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "status: $status"
    }

    def deleteBuggyBranch(){
        ProcessBuilder builder = new ProcessBuilder("git", "branch", "-D", buggyMutantBranchName)
        builder.directory(new File(localPath))
        Process process = builder.start()
        def status = process.waitFor()
        process.inputStream.readLines()
        process.inputStream.close()
        log.info "status: $status"
    }

}
