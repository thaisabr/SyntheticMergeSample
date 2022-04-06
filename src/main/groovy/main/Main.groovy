package main

import bugManager.BugManager
import groovy.util.logging.Slf4j

@Slf4j
class Main {

    static void main(String[] args){
        BugManager bugManager = new BugManager()
        bugManager.run()
    }

}
