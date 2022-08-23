package bugManager

import au.com.bytecode.opencsv.CSVReader
import au.com.bytecode.opencsv.CSVWriter
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
    String syntheticMergesCsv
    final int BUGS_LIMIT

    BugManager(int limit){
        this.BUGS_LIMIT = limit
        this.defects4jPath = verifyCurrentFolder()
        this.projectsFolder = "${this.defects4jPath}${File.separator}framework${File.separator}projects${File.separator}"
        this.bugsFolder = "${defects4jPath}${File.separator}bugs"
        this.buggyRevisionFolder = "${bugsFolder}${File.separator}buggy"
        this.fixedRevisionFolder = "${bugsFolder}${File.separator}fixed"
        this.bugFiles = []
        this.bugs = []
        this.mutantsManagerList = []
        this.syntheticMerges = []
        this.syntheticMergesCsv = "${defects4jPath}${File.separator}syntheticMerges.csv"

        createSyntheticMergesCsv()

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

    BugManager(String project, int limit){
        this(limit)
        this.projects = [project]
    }

    private createSyntheticMergesCsv(){
        def file = new File(syntheticMergesCsv)
        if(file.exists()){
            file.delete()
        }
        String[] header = ["BUG_ID", "BUG_PROJECT", "MERGE FOLDER", "MUTANT FOLDER"]
        List<String[]> content = []
        content += header
        exportSyntheticMerges(content)
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
        //PROVISÓRIO
        this.projects = ["Closure", "Math", "Lang", "Mockito", "Time", "Chart"
                         //, "Cli", "Codec", "Collections", "Compress", "Csv", "Gson", "JacksonCore",
                         //"JacksonDatabind", "JacksonXml", "Jsoup", "JxPath"
        ]
    }

    private void startDefect4jService(){
        ProcessBuilder builder = new ProcessBuilder("./init.sh")
        builder.directory(new File(defects4jPath))
        Process process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "Status when starting defects4j: $status"
    }

    private void addDefect4jServiceToThePath(){
        ProcessBuilder builder = new ProcessBuilder("./export_defects4j.sh")
        builder.directory(new File(defects4jPath))
        builder.inheritIO()
        Process process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "Status when adding defects4j to the path: $status"
    }

    private void configureMajorOpt(){
        ProcessBuilder builder = new ProcessBuilder("./export_major.sh")
        builder.directory(new File(defects4jPath))
        builder.inheritIO()
        Process process = builder.start()
        def status = process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "Status when executing export_major.sh: $status"
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
            entries?.subList(0, BUGS_LIMIT)?.each{ entry ->
                def id = entry[0]
                def project = entry[1]
                def modifiedClasses = entry[7].substring(0, entry[7].size()).tokenize(';')
                def failingTests = entry[8].substring(0, entry[8].size()).tokenize(';')
                def buggyRevision = entry[5]
                def fixedRevision = entry[6]
                this.bugs += new Bug(id: id, project: project, failingTests: failingTests, modifiedClasses: modifiedClasses,
                        buggyFolder:"${buggyRevisionFolder}${File.separator}${project}_${id}",
                        fixedFolder:"${fixedRevisionFolder}${File.separator}${project}_${id}",
                        buggyRevision: buggyRevision,
                        fixedRevision: fixedRevision)
            }
        }
    }

    void checkoutBuggyRevision(Bug bug){
        ProcessBuilder builder = new ProcessBuilder("defects4j", "checkout", "-p", bug.project, "-v",
                "${bug.id}b", "-w", bug.buggyFolder)
        builder.directory(new File(defects4jPath))
        Process process = builder.start()
        process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "Defects4J's checked out the buggy revision: '${bug.buggyFolder}'"
    }

    private void checkoutBuggyRevisions(){
        this.bugs.each { bug ->
            checkoutBuggyRevision(bug)
        }
    }

    void checkoutFixedRevision(Bug bug){
        ProcessBuilder builder = new ProcessBuilder("defects4j", "checkout", "-p", bug.project, "-v",
                "${bug.id}f", "-w", bug.fixedFolder)
        builder.directory(new File(defects4jPath))
        Process process = builder.start()
        process.waitFor()
        process.inputStream.eachLine { log.info it.toString() }
        process.inputStream.close()
        log.info "Defects4J's checked out the fixed revision: '${bug.fixedFolder}'"
    }

    private void generateMutantsForBuggyRevisions(){
        mutantsManagerList = []
        def buggyFolders = new File(buggyRevisionFolder).listFiles()?.collect{it.absolutePath }

        bugs.each{ bug ->
            def folder = buggyFolders.find{ it.endsWith("${bug.project}_${bug.id}") }
            if(folder){
                def tests = bug.failingTests
                def coveredMethods = []

                for(int i=0; i<tests.size(); i++) {
                    def test = bug.failingTests.first()
                    //executa teste com cobertura
                    bug.executeTestWithCoverage(test)

                    //identifica os métodos cobertos pelos testes
                    CoverageManager cm = new CoverageManager(bug.buggyFolder)
                    cm.configureCoveredMethods()
                    coveredMethods += cm.coveredMethods
                    coveredMethods = coveredMethods.unique()
                }

                //gera mutantes
                MutantsManager mm = new MutantsManager(bug, coveredMethods, defects4jPath)
                boolean generateMutant = mm.run()
                if(generateMutant) mutantsManagerList += mm
            }
       }
    }

    private void generateSyntheticMerges(){
        def buggyFolders = new File(buggyRevisionFolder).listFiles()?.collect{it.absolutePath }

        for(int i=0; i<this.bugs.size(); i++){
            Bug bug = this.bugs.get(i)
            log.info "Generating synthetic merge for bug '${bug.id}' from project '${bug.project}'"

            def folder = buggyFolders.find{ it.endsWith("${bug.project}_${bug.id}") }
            if(!folder) continue
            MutantsManager mm = mutantsManagerList.find{ it.bug == bug }
            if(!mm) continue

            boolean foundFailedTest
            SyntheticMerge syntheticMerge
            for(int j=0; j<mm.mutantsName.size(); j++){
                String mutantFolder = mm.mutantsName.get(j)
                String mutantPath = "${mm.mutantsFolder}${File.separator}${mutantFolder}"
                log.info "Trying mutant '${mutantPath}'"

                GitManager gm = new GitManager(bug.buggyFolder, mutantPath, bug.fixedFolder,
                       bug.fixedRevision)

                def result = gm.run()
                if(!result){
                    log.error "It was not possible to generate synthetic merge using $mutantPath"
                    continue
                }

                foundFailedTest = false
                for(int k=0; k<bug.failingTests.size(); k++){
                    boolean passed = bug.executeTest(bug.failingTests.get(k))
                    def resultToShow = passed ? "Passed" : "Failed"
                    log.info "Test execution after merge: ${resultToShow}"
                    if(!passed){
                        foundFailedTest = true
                        break
                    }
                }

                if(foundFailedTest) {
                    syntheticMerge = new SyntheticMerge(bug:bug, mutantPath:mutantPath, conflictCheckoutFolder:bug.fixedFolder)
                    this.syntheticMerges += syntheticMerge
                    saveSyntheticMerge(syntheticMerge)
                    break
                }

                result = gm.restore()
                if(!result){
                    log.error "It was not possible to restore the original branch when generating synthetic merges using $mutantPath"
                }
            }
            log.info "Partial number of generated synthetic merges: ${this.syntheticMerges.size()}"
        }
    }

    private void saveSyntheticMerge(SyntheticMerge sm){
        List<String[]> content = []
        def temp = [sm.bug.id, sm.bug.project, sm.conflictCheckoutFolder, sm.mutantPath]
        log.info temp.toString()
        content += temp as String[]
        exportSyntheticMerges(content)
    }

    private void exportSyntheticMerges(List content){
        def file = new File(syntheticMergesCsv)
        CSVWriter writer = new CSVWriter(new FileWriter(file, true))
        writer.writeAll(content)
        writer.close()
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
        //startDefect4jService()
        //log.info "Defects4J was initialized"

        //adiciona os executáveis do Defects4J ao path
        //addDefect4jServiceToThePath()
        //log.info "Defects4J's executables were added to the path"

        //configuração para salvar os mutantes gerados
        //configureMajorOpt()

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

        log.info "Number of generated synthetic merges: ${this.syntheticMerges.size()}"
    }

}
