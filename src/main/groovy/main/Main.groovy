package main

import bugManager.BugManager
import groovy.util.logging.Slf4j

@Slf4j
class Main {

    static void main(String[] args){
        BugManager bugManager
        def projects = ["Chart", "Cli", "Closure", "Codec", "Collections", "Compress", "Csv", "Gson", "JacksonCore",
                        "JacksonDatabind", "JacksonXml", "Jsoup", "JxPath", "Lang", "Math", "Mockito", "Time"]
        def project
        if(args && (args.size()==1)) {
            project = args[0]
            if(!(project in projects)) project = null
        }
        if(project){
            bugManager = new BugManager(project)
        } else {
            bugManager = new BugManager()
        }
        bugManager.run()
    }

}
