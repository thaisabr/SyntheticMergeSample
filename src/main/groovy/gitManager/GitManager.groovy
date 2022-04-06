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
        createMutantBranch()
        copyMutant()
        checkoutFixedBranch()
        merge()
    }

    def copyMutant(){
        ProcessBuilder builder = new ProcessBuilder("cp", "-r", "${buggyMutantFolder}${File.separator}.",
                "src${File.separator}main${File.separator}java")
        builder.directory(new File(localPath))
        Process process = builder.start()
        process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
    }

    def checkoutFixedBranch(){
        def builder = new ProcessBuilder('git','checkout', fixedBranchName)
        builder.directory(new File(localPath))
        def process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        status
    }

    def createMutantBranch(){
        def builder = new ProcessBuilder('git','checkout', '-b', buggyMutantBranchName)
        builder.directory(new File(localPath))
        def process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        status
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

    def merge(){
        def builder = new ProcessBuilder('git','merge', buggyMutantBranchName)
        builder.directory(new File(localPath))
        def process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        status
    }

}
