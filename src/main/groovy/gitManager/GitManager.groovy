package gitManager

import groovy.util.logging.Slf4j

@Slf4j
class GitManager {

    String mainBranch
    String fixingRevisionCommit
    String originalBugFolder
    String buggyMutantFolder
    String fixedBugFolder
    String detachedName
    String buggyMutantBranchName
    String fixBranchName

    GitManager(String originalBugFolder, String buggyMutantFolder, String fixedFolder, String fixingRevisionCommit){
        this.originalBugFolder = originalBugFolder
        this.buggyMutantFolder = buggyMutantFolder
        this.fixedBugFolder = fixedFolder
        this.fixingRevisionCommit = fixingRevisionCommit
        this.buggyMutantBranchName = "mutantSpg2022"
        this.fixBranchName = "fixSpg2022"
        this.mainBranch = "masterSpg2022"
    }

    boolean run() throws Exception {
        log.info "localpath: ${originalBugFolder}"
        if(detachedName==null) {
            detachedName = verifyCurrentBranch()
            log.info "detached HEAD: ${detachedName}"
        }

        //cria masterSpg2022 branch a partir da detached head que representa o commit que introduziu o bug
        createAndCheckoutMainBranch()
        def currentBranch = verifyCurrentBranch()
        log.info "currentBranch: ${currentBranch}"
        if(currentBranch!=mainBranch){
            log.error "It is not possible to create and checkout '${mainBranch}'"
            return false
        }

        //cria branch para a correção
        configureFixBranch()

        //volta ao branch principal (masterSpg2022)
        checkoutMainBranch()
        currentBranch = verifyCurrentBranch()
        log.info "currentBranch: ${currentBranch}"

        //cria branch para o bug mutante
        configureMutantBranch()

        currentBranch = verifyCurrentBranch()
        log.info "current branch: ${currentBranch}"

        //Integra a branch que corrige o bug com a branch mutante
        mergeFixBranch()
        log.info "We merged branches ${currentBranch} and ${fixBranchName}"

        currentBranch = verifyCurrentBranch() //branch mutante
        log.info "current branch: ${currentBranch}"

        //na pasta em que foi feito checkout da versão corrigida
        updateFixedBranchWithIntegrationResult()

        true
    }

    private updateFixedBranchWithIntegrationResult(){
        def srcFolder= new File(originalBugFolder).listFiles().findAll{ it.isDirectory() }.find{
            it.absolutePath.endsWith("src") || it.absolutePath.endsWith("source")
        }
        def srcFolderSufix = ""
        def index = srcFolder.absolutePath.lastIndexOf(File.separator)
        srcFolderSufix = srcFolder.absolutePath.substring(index)

        def origin = "${originalBugFolder}${File.separator}${srcFolderSufix}"
        def destiny = "${fixedBugFolder}${File.separator}${srcFolderSufix}"
        def currentFolder = fixedBugFolder
        log.info "localpath: ${fixedBugFolder}"
        def status = copy(origin, currentFolder, destiny)
        log.info "Status copying integration result: $status"
        status
    }

    boolean restore(){
        checkoutDetached()
        def currentBranch = verifyCurrentBranch()
        log.info "current branch: ${currentBranch}"

        def status = deleteBranch(mainBranch)
        log.info "Status deleting main branch: $status"

        status = deleteBranch(fixBranchName)
        log.info "Status deleting fix branch: $status"

        status = deleteBranch(buggyMutantBranchName)
        log.info "Status deleting buggy branch: $status"

        restoreFixFolder()

        true
    }

    private boolean restoreFixFolder(){
        ProcessBuilder builder = new ProcessBuilder("git", "restore", ".")
        builder.directory(new File(fixedBugFolder))
        Process process = builder.start()
        builder.inheritIO()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "Status restoring fixing folder: $status"
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

    private configureMutantBranch() throws Exception {
        def status = createAndCheckoutBranch(buggyMutantBranchName)
        log.info "Status creating mutant branch: $status"
        if(status != 0){
            log.error "Error while creating mutant branch. Status ${status}."
            throw new Exception("Error while creating mutant branch")
        } else {
            log.info "Mutant branch was created and checked out"
        }
        def currentBranch = verifyCurrentBranch()
        log.info "current branch: ${currentBranch}"

        def destiny = copyMutant()
        log.info "Mutant branch was changed"

        versioningSource(destiny)
        log.info "Changes in mutant branch were versioned"

        commitMutant()
        log.info "Changes in mutant branch were commited"
    }

    def configureFixBranch(){
        def status = createAndCheckoutBranch(fixBranchName)
        log.info "Status creating fix branch: $status"
        log.info "Fix branch was created and checked out"
        def currentBranch = verifyCurrentBranch()
        log.info "current branch: ${currentBranch}"

        def destiny = copyFix()
        log.info "Fix branch was changed"

        versioningSource(destiny)
        log.info "Changes in fix branch were versioned"

        commitFix()
        log.info "Changes in fix branch were commited"
    }

    def checkoutBranch(String branch){
        def builder = new ProcessBuilder('git','checkout', branch)
        builder.directory(new File(originalBugFolder))
        def process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        status
    }

    def checkoutMainBranch(){
        def status = checkoutBranch(mainBranch)
        log.info "Status checked out main branch: $status"
    }

    def checkoutDetached(){
        def status = checkoutBranch(detachedName)
        log.info "Status checked out detached: $status"
    }

    def checkoutCommit(String sha){
        def builder = new ProcessBuilder('git','checkout', sha)
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

    def createAndCheckoutBranch(String branch){
        def builder = new ProcessBuilder('git','checkout', '-b', branch)
        builder.directory(new File(originalBugFolder))
        def process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        status
    }

    private int copyFixVersion(String destiny){
        def origin = configureOrigin(destiny)
        log.info "Fix will be copyied from '${origin}' to '${destiny}' "
        def currentFolder = originalBugFolder
        def status = copy(origin, currentFolder, destiny)
        log.info "Status copying fix: $status"
        status
    }

    def configureOrigin(String destiny){
        int index = destiny.lastIndexOf(File.separator)
        def sufix = ""
        if(index>-1) sufix = destiny.substring(index)
        def origin = fixedBugFolder + sufix
    }

    def copyFix(){
        String destiny = ""
        def srcFolder= new File(originalBugFolder).listFiles().findAll{ it.isDirectory() }.find{
            it.absolutePath.endsWith("src") || it.absolutePath.endsWith("source")
        }

        if(srcFolder) destiny = srcFolder.absolutePath
        else {
            def gsonFolder = new File(originalBugFolder).listFiles().find{ it.isDirectory() && it.absolutePath.endsWith("gson") }
            if(gsonFolder) destiny = gsonFolder.absolutePath
            else {
                log.info "It is not possible to copy the fix because we cannot find de source folder."
                return
            }
        }
        copyFixVersion(destiny)
        destiny
    }

    private int copyMutantVersion(String destiny){
        def currentFolder = originalBugFolder
        def origin = buggyMutantFolder
        log.info "Mutant will be copyied from '${origin}' to '${destiny}' "
        def status = copy(origin, currentFolder, destiny)
        log.info "Status copying mutant: $status"
        status
    }

    private int copy(String origin, String currentFolder, String destiny){
        ProcessBuilder builder = new ProcessBuilder("cp", "-r", "${origin}${File.separator}.", destiny)
        builder.directory(new File(currentFolder))
        builder.inheritIO()
        Process process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        status
    }

    def copyMutant(){
        String destiny = ""
        def srcFolder= new File(originalBugFolder).listFiles().findAll{ it.isDirectory() }.find{
            it.absolutePath.endsWith("src") || it.absolutePath.endsWith("source")
        }

        if(srcFolder) destiny = srcFolder.absolutePath
        else {
            def gsonFolder = new File(originalBugFolder).listFiles().find{ it.isDirectory() && it.absolutePath.endsWith("gson") }
            if(gsonFolder) destiny = gsonFolder.absolutePath + File.separator + "src" + File.separator + "main" + File.separator + "java"
            else {
                log.info "It is not possible to copy the mutant because we cannot find de source folder."
                return
            }
        }

        if(destiny.endsWith("src")) {
            def hasMainJavaFolder = false
            def aux1 = new File("${originalBugFolder}${File.separator}src").listFiles().find{
                it.isDirectory() && it.absolutePath.endsWith("main")}
            if(aux1!=null){
                destiny = destiny + File.separator + "main" + File.separator + "java"
                hasMainJavaFolder = true
            }

            if(!hasMainJavaFolder){
                def aux2 = new File("${originalBugFolder}${File.separator}src").listFiles().find{
                    it.isDirectory() && it.absolutePath.endsWith("java")}
                if(aux2!=null){
                    destiny = destiny + File.separator + "java"
                }
            }
        }
        copyMutantVersion(destiny)
        destiny
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

    def commit(String message){
        def builder = new ProcessBuilder('git','commit','-m', message)
        builder.directory(new File(originalBugFolder))
        builder.inheritIO()
        def process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        status
    }

    def commitMutant(){
        def status = commit('buggy mutant')
        log.info "Status commiting mutant: $status"
    }

    def commitFix(){
        def status = commit('fix')
        log.info "Status commiting fx: $status"
    }

    def mergeBranch(String branch){
        def builder = new ProcessBuilder('git','merge', '--no-edit', '--no-ff', branch)
        builder.directory(new File(originalBugFolder))
        def process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        status
    }

    def mergeMutantBuggyBranch(){
        def status = mergeBranch(buggyMutantBranchName)
        log.info "Status merging buggy branch: $status"
    }

    def mergeFixBranch(){
        def status = mergeBranch(fixBranchName)
        log.info "Status merging fix branch: $status"
    }

    def deleteBranch(String branch){
        ProcessBuilder builder = new ProcessBuilder("git", "branch", "-D", branch)
        builder.directory(new File(originalBugFolder))
        Process process = builder.start()
        def status = process.waitFor()
        process.inputStream.readLines()
        process.inputStream.close()
        status
    }

}
