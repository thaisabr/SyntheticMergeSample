package gitManager

import groovy.util.logging.Slf4j

@Slf4j
class GitManager {

    String mainBranch
    String fixingRevisionCommit
    String originalBugFolder
    String buggyMutantFolder
    String detachedName
    String buggyMutantBranchName

    GitManager(String originalBugFolder, String buggyMutantFolder, String fixingRevisionCommit){
        this.originalBugFolder = originalBugFolder
        this.buggyMutantFolder = buggyMutantFolder
        this.fixingRevisionCommit = fixingRevisionCommit
        this.buggyMutantBranchName = "mutantSpg2022"
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

        //Integra a branch mutante com a branch que corrige o bug
        mergeBuggyBranch()
        log.info "We merged branches ${currentBranch} and ${buggyMutantBranchName}"

        currentBranch = verifyCurrentBranch()
        log.info "current branch: ${currentBranch}"

        true
    }

    boolean restore(){
        checkoutDetached()
        def currentBranch = verifyCurrentBranch()
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

        def destiny = copyMutant()
        log.info "Mutant branch was changed"

        versioningSource(destiny)
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

    private int copyMutantVersion(String destiny){
        log.info "Mutant will be copyied from '${buggyMutantFolder}' to '${destiny}' "
        ProcessBuilder builder = new ProcessBuilder("cp", "-r", "${buggyMutantFolder}${File.separator}.", destiny)
        builder.directory(new File(originalBugFolder))
        builder.inheritIO()
        Process process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "status copying mutant: $status"
        status
    }

    def copyMutant(){
        def destiny
        def originFolder= new File(originalBugFolder).listFiles().findAll{ it.isDirectory() }.find{
            it.absolutePath.endsWith("src") || it.absolutePath.endsWith("source")
        }

        destiny = originFolder.absolutePath
        if(destiny.endsWith("src")) {
            def aux = new File("${buggyMutantFolder}${File.separator}src").listFiles().findAll{ it.isDirectory() }
            def contains = aux.find{ it.absolutePath.endsWith("main") }
            if(contains){
                destiny = destiny + File.separator + "main" + File.separator + "java"
            }
        }
        copyMutantVersion(destiny)
        return destiny
    }

    def versioningSource(String destiny){
        ProcessBuilder builder = new ProcessBuilder("git", "add", "${destiny}${File.separator}.")
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
