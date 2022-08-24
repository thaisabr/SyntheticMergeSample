package main

import bugManager.BugManager
import groovy.util.logging.Slf4j

@Slf4j
class Main {

    static void main(String[] args){
        BugManager bugManager
        def projects = ["Chart", "Cli", "Closure", "Codec", "Collections", "Compress", "Csv", "Gson", "JacksonCore",
                        "JacksonDatabind", "JacksonXml", "Jsoup", "JxPath", "Lang", "Math", "Mockito", "Time"]
        String project
        if(args && (args.size()==1)) {
            project = args[0]
            if(!(project in projects)) project = null
        }
        if(project){
            log.info "Generating bugs for project '${project}'"
            bugManager = new BugManager(project)
        } else {
            log.info "Generating bugs for 17 projects"
            bugManager = new BugManager(50)
        }
        bugManager.run()
    }

}
