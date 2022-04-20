package gitManager

import groovy.util.logging.Slf4j

@Slf4j
class AlternativeGitManager {

    String mainBranch
    String fixingRevisionCommit
    String originalBugFolder
    String buggyMutantFolder
    String detachedName
    String originalBuggyBranchName
    String buggyMutantBranchName
    String fixedBugFolder

    AlternativeGitManager(String originalBugFolder, String buggyMutantFolder, String fixedBugFolder,
                         String fixingRevisionCommit){
        this.originalBugFolder = originalBugFolder
        this.buggyMutantFolder = buggyMutantFolder
        this.fixedBugFolder = fixedBugFolder
        this.fixingRevisionCommit = fixingRevisionCommit
        this.buggyMutantBranchName = "mutantSpg2022"
        this.originalBuggyBranchName = "bugSpg2022"
        this.mainBranch = "masterSpg2022"
    }

    boolean run(){
        log.info "localpath: ${originalBugFolder}"
        detachedName = verifyCurrentBranch()
        log.info "detached HEAD: ${detachedName}"

        //cria masterSpg2022 branch a partir da detached head que representa o commit que introduziu o bug
        createAndCheckoutMainBranch()
        def currentBranch = verifyCurrentBranch()
        log.info "currentBranch: ${currentBranch}"
        if(currentBranch!=mainBranch){
            log.error "It is not possible to create and checkout '${mainBranch}'"
            return false
        }

        //cria branche para o bug mutante
        configureMutantBranch()

        //volta para a branch principal, que possui o bug original
        checkoutMainBranch()
        currentBranch = verifyCurrentBranch()
        log.info "currentBranch: ${currentBranch}"
        if(currentBranch!=mainBranch){
            log.error "It is not possible to checkout '${mainBranch}'"
            return false
        }

        //Integra o commit que faz a correção na branch principal
        mergeCommit(fixingRevisionCommit)
        log.info "We merged the fixing revision commit"

        currentBranch = verifyCurrentBranch()
        log.info "current branch: ${currentBranch}"

        mergeBuggyBranch()
        log.info "We merged branches ${currentBranch} and ${buggyMutantBranchName}"

        currentBranch = verifyCurrentBranch()
        log.info "current branch: ${currentBranch}"

        checkoutDetached()
        currentBranch = verifyCurrentBranch()
        log.info "current branch: ${currentBranch}"

        deleteMainBranch()

        deleteBuggyBranch()

        true
    }

    private createAndCheckoutMainBranch(){
        def builder = new ProcessBuilder('git', 'checkout', '-b', mainBranch)
        builder.directory(new File(originalBugFolder))
        def process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "status checked out main branch: $status"

    }

    private configureMutantBranch(){
        createAndCheckoutMutantBranch()
        log.info "Mutant branch was created and checked out"
        def currentBranch = verifyCurrentBranch()
        log.info "current branch: ${currentBranch}"

        copyMutant()
        log.info "Mutant branch was changed"

        versioningSource()
        log.info "Changes in mutant branch were versioned"

        commitMutant()
        log.info "Changes in mutant branch were commited"
    }

    def checkoutMainBranch(){
        def builder = new ProcessBuilder('git','checkout', mainBranch)
        builder.directory(new File(originalBugFolder))
        def process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "status checked out main branch: $status"
    }

    def checkoutDetached(){
        def builder = new ProcessBuilder('git','checkout', detachedName)
        builder.directory(new File(originalBugFolder))
        def process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "status checked out detached: $status"
    }

    def mergeCommit(String sha){
        def builder = new ProcessBuilder('git','merge', sha)
        builder.directory(new File(originalBugFolder))
        def process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        status
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

    def createAndCheckoutMutantBranch(){
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

    def mergeBuggyBranch(){
        def builder = new ProcessBuilder('git','merge', buggyMutantBranchName)
        builder.directory(new File(originalBugFolder))
        def process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "status merging buggy branch: $status"
    }

    def deleteMainBranch(){
        ProcessBuilder builder = new ProcessBuilder("git", "branch", "-D", mainBranch)
        builder.directory(new File(originalBugFolder))
        Process process = builder.start()
        def status = process.waitFor()
        process.inputStream.readLines()
        process.inputStream.close()
        log.info "status deleting main branch: $status"
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

}
