package bugManager

import au.com.bytecode.opencsv.CSVReader
import gitManager.GitManager
import groovy.util.logging.Slf4j
import coverageManager.CoverageManager
import syntheticMerge.SyntheticMerge

@Slf4j
class BugManager {

    String defects4jPath
    String projectsFolder
    String bugsFolder
    String buggyRevisionFolder
    String fixedRevisionFolder
    List<String> projects
    List<String> bugFiles
    List<Bug> bugs
    List<MutantsManager> mutantsManagerList
    List<SyntheticMerge> syntheticMerges

    BugManager(){
        this(verifyCurrentFolder())
    }

    BugManager(String defects4jPath){
        this.defects4jPath = defects4jPath
        this.projectsFolder = "${this.defects4jPath}${File.separator}framework${File.separator}projects${File.separator}"
        this.bugsFolder = "${defects4jPath}${File.separator}bugs"
        this.buggyRevisionFolder = "${bugsFolder}${File.separator}buggy"
        this.fixedRevisionFolder = "${bugsFolder}${File.separator}fixed"
        this.bugFiles = []
        this.bugs = []
        this.mutantsManagerList = []
        this.syntheticMerges = []

        log.info "defects4jPath: ${defects4jPath}"
        log.info "projectsFolder: ${projectsFolder}"
        log.info "bugsFolder: ${bugsFolder}"
        log.info "buggyRevisionFolder: ${buggyRevisionFolder}"
        log.info "fixedRevisionFolder: ${fixedRevisionFolder}"

        //cria as pastas "bugs/buggy" e "bugs/fixed" internas à pasta do defects4j
        createCheckoutFolders()

        //informa os projetos para os quais serão criados merges sintéticos
        initializeProjects()
    }

    private createCheckoutFolders(){
        deleteFolder(bugsFolder)
        createFolder(bugsFolder)
        deleteFolder(buggyRevisionFolder)
        createFolder(buggyRevisionFolder)
        deleteFolder(fixedRevisionFolder)
        createFolder(fixedRevisionFolder)
    }

    private void initializeProjects(){
        this.projects = ["Chart"/*, "Cli", "Closure", "Codec", "Collections", "Compress", "Csv", "Gson", "JacksonCore",
                         "JacksonDatabind", "JacksonXml", "Jsoup", "JxPath", "Lang", "Math", "Mockito", "Time"*/]
    }

    private void startDefect4jService(){
        ProcessBuilder builder = new ProcessBuilder("./init.sh")
        builder.directory(new File(defects4jPath))
        Process process = builder.start()
        process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
    }

    private void addDefect4jServiceToThePath(){
        ProcessBuilder builder = new ProcessBuilder("./export_defects4j.sh")
        builder.directory(new File(defects4jPath))
        builder.inheritIO()
        Process process = builder.start()
        process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
    }

    private void generateBugCsv(){
        this.projects.each{ project ->
            def file = "${project}_bugs.csv"
            log.info "Generating file: $file"
            ProcessBuilder builder = new ProcessBuilder("defects4j", "query", "-p", project, "-q",
                    "bug.id,project.id,project.name,project.repository,project.bugs.csv,revision.id.buggy,revision.id.fixed,classes.modified,tests.trigger",
                    "-o", file)
            builder.directory(new File(defects4jPath))
            builder.inheritIO()
            Process process = builder.start()
            def status = process.waitFor()
            process.inputStream.eachLine { log.info it.toString() }
            process.inputStream.close()
            log.info "status: $status"
        }
    }

    private initializeBugFileList(){
        def file = new File(defects4jPath)
        bugFiles = file.list().findAll{it.endsWith("_bugs.csv") }
    }

    private void generateBugList(){
        this.bugs = []
        this.bugFiles.each{ bugFile ->
            List<String[]> entries = readCsv(bugFile)
            entries?.each{ entry ->
                def id = entry[0]
                def project = entry[1]
                def modifiedClasses = entry[7].substring(0, entry[7].size()).tokenize(';')
                def failingTests = entry[8].substring(0, entry[8].size()).tokenize(';')
                this.bugs += new Bug(id: id, project: project, failingTests: failingTests, modifiedClasses: modifiedClasses,
                        buggyFolder:"${buggyRevisionFolder}${File.separator}${project}_${id}",
                        fixedFolder:"${fixedRevisionFolder}${File.separator}${project}_${id}")
            }
        }
    }

    private void checkoutBuggyRevisions(){
        this.bugs.each { bug ->
            ProcessBuilder builder = new ProcessBuilder("defects4j", "checkout", "-p", bug.project, "-v",
                    "${bug.id}b", "-w", bug.buggyFolder)
            builder.directory(new File(defects4jPath))
            Process process = builder.start()
            process.waitFor()
            process.inputStream.eachLine { log.info it.toString() }
            process.inputStream.close()
            log.info "Defects4J's checked out the buggy revision: '${bug.buggyFolder}'"
        }
    }

    private void checkoutFixedRevision(Bug bug){
        deleteFolder(bug.fixedFolder)
        ProcessBuilder builder = new ProcessBuilder("defects4j", "checkout", "-p", bug.project, "-v",
                "${bug.id}f", "-w", bug.fixedFolder)
        builder.directory(new File(defects4jPath))
        Process process = builder.start()
        process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
    }

    private void generateMutantsForBuggyRevisions(){
        mutantsManagerList = []
        def buggyFolders = new File(buggyRevisionFolder).listFiles()?.collect{it.absolutePath }

        bugs.each{ bug ->
            def folder = buggyFolders.find{ it.endsWith("${bug.project}_${bug.id}") }
            if(folder){
                def test = bug.failingTests.first()
                //executa teste com cobertura
                bug.executeTestWithCoverage(test)

                //identifica os métodos cobertos pelos testes
                CoverageManager cm = new CoverageManager(bug.buggyFolder)
                cm.configureCoveredMethods()

                //gera mutantes
                MutantsManager mm = new MutantsManager(bug, cm.coveredMethods, defects4jPath)
                mm.run()
                mutantsManagerList += mm
            }
        }
    }

    private void generateSyntheticMerges(){
        def syntheticMerges = []
        def buggyFolders = new File(buggyRevisionFolder).listFiles()?.collect{it.absolutePath }

        for(int i=0; i<bugs.size(); i++){
            Bug bug = bugs.get(i)
            def folder = buggyFolders.find{ it.endsWith("${bug.project}_${bug.id}") }
            if(!folder) continue
            MutantsManager mm = mutantsManagerList.find{ it.bug == bug }
            if(!mm) continue

            boolean foundFailedTest
            SyntheticMerge syntheticMerge
            for(int j=0; j<mm.mutantsName.size(); j++){
                String mutantFolder = mm.mutantsName.get(j)
                String mutantPath = "${mm.mutantsFolder}${File.separator}${mutantFolder}"

                checkoutFixedRevision(bug)

                GitManager gm = new GitManager(fixedRevisionFolder, mutantPath)
                gm.run()

                foundFailedTest = false
                for(int k=0; k<bug.failingTests.size(); k++){
                    boolean passed = bug.executeTest(bug.failingTests.get(k))
                    if(!passed){
                        foundFailedTest = true
                        break
                    }
                }

                if(foundFailedTest) {
                    syntheticMerge = new SyntheticMerge(bug:bug, mutantPath:mutantPath, conflictCheckoutFolder:bug.fixedFolder)
                    syntheticMerges += syntheticMerge
                    break
                }
            }
        }
    }

    private void listSyntheticMerges(){
        log.info "List of synthetic merges: ${syntheticMerges.size()}"
        syntheticMerges.each{ sm ->
            log.info "${sm.conflictCheckoutFolder}"
        }
    }

    private static verifyCurrentFolder(){
        ProcessBuilder builder = new ProcessBuilder("pwd")
        builder.directory(new File("."))
        Process process = builder.start()
        process.waitFor()
        def currentFolder = process.inputStream.readLines().first()
        process.inputStream.close()
        currentFolder
    }

    private static createFolder(String folder){
        def f = new File(folder)
        if(!f.exists()) {
            f.mkdir()
        }
    }

    private static List<String[]> readCsv(String filename) {
        List<String[]> entries = []
        File file = new File(filename)
        if(!file.exists()) return entries
        try {
            CSVReader reader = new CSVReader(new FileReader(file))
            entries = reader.readAll()
            reader.close()
        } catch (Exception ex) {
            log.error ex.message
        }
        entries
    }

    private static deleteFolder(String folder) {
        emptyFolder(folder)
        def dir = new File(folder)
        dir.deleteDir()
    }

    private static emptyFolder(String folder) {
        def dir = new File(folder)
        def files = dir.listFiles()
        if (files != null) {
            files.each { f ->
                if (f.isDirectory()) emptyFolder(f.getAbsolutePath())
                else f.delete()
            }
        }
    }

    void run(){
        //inicializa o serviço do Defects4J
        startDefect4jService()
        log.info "Defects4J was initialized"

        //adiciona os executáveis do Defects4J ao path
        addDefect4jServiceToThePath()
        log.info "Defects4J's executables were added to the path"

        //gera um arquivo "projeto_bugs.csv" para cada projeto a se gerar merges sintéticos, resumindo informações
        //sobre os bugs ativos
        generateBugCsv()

        //organiza listagem de arquivos com sufixo "_bugs.csv" gerados na etapa anterior
        initializeBugFileList()
        log.info "Defects4J generated ${this.bugFiles.size()} bug files"

        //gera uma listagem de bugs a partir da leitura dos arquivos "projeto_bugs.csv" gerados anteriormente
        generateBugList()
        log.info "Defects4J generated a list of ${this.bugs.size()} bugs"

        //faz checkout de todos os bugs dos projetos selecionados para geração de merges sintéticos
        checkoutBuggyRevisions()

        //gera mutantes para cada revisão bugada
        generateMutantsForBuggyRevisions()

        //gera merges sintéticos
        generateSyntheticMerges()

        //exibe listagem de merges sintéticos
        listSyntheticMerges()
    }

}
