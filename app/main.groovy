import groovy.io.FileType
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import groovy.json.*
import static Config.*


class Config {

    public static String namespace = System.getenv('CI_COMMIT_REF_NAME') ?: 'unknown'

}

def main(arg) {
    print """


  _____  ________      ______   ____  _____   _____ 
 |  __ \\|  ____\\ \\    / / __ \\ / __ \\|  __ \\ / ____|
 | |  | | |__   \\ \\  / / |  | | |  | | |__) | (___  
 | |  | |  __|   \\ \\/ /| |  | | |  | |  ___/ \\___ \\ 
 | |__| | |____   \\  / | |__| | |__| | |     ____) |
 |_____/|______|   \\/   \\____/ \\____/|_|    |_____/ 
                                                    
         
                                                 
"""
    println "0. Prepare to work with namespace ${namespace} at ${new File('.').absolutePath}"
    convertCompose(arg)
    changeYml(arg)
    updateRancher()
}

def convertCompose(String arg) {
    println "1. Convert docker-compose.yml to kubernetes conf"
    try {
        if (arg ==~ /--clean|-c/) {
            println "   Clean config.yml file"
            new File('config.yml').delete()
        }
        def file = new File('docker-compose.yml')
        def proc = ["kompose", "-v", "-f", file.absolutePath, "convert"].execute()
        def b = new StringBuffer()
        proc.waitForProcessOutput(b, b)
        if (!b.toString().empty) {
            println b.toString().replaceAll("^", "   ")
                    .replaceAll("\n", "\n   ")
                    .replaceAll("\n\\s+\$", "")
        }
        println "1. Done"
    } catch (e) {
        println "1. $e.message"
    }
}

@Grab('org.yaml:snakeyaml:1.17')
def changeYml(String arg) {
    println "2. Modify kubernetes configuration files"
    def currentDir = new File('.')
    def output = []
    def outputFile = new File("config.yml")
    DumperOptions options = new DumperOptions()
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK)
    Yaml yaml = new Yaml(options)

    println "   Found ${currentDir.listFiles().findAll { it.name ==~ /.*yaml/ }.size()} files"
    currentDir.eachFileMatch(FileType.ANY, ~/.*yaml/) {
        println "   Work with $it.name"
        def configFile = yaml.load(convertText(it.text))
        convertYaml(configFile)
        output << yaml.dump(configFile)
    }

    outputFile.text = output.join('---\n')
    if (arg ==~ /--clean|-c/) {
        println "   Clean kubernetes configuration files"
        new File('.').eachFileMatch(FileType.ANY, ~/.*yaml/) { it.delete() }
    }

    println "2. Done"
}

private List<Object> convertYaml(configFile) {
    configFile.metadata.annotations
            .findAll { it.key ==~ /kompose\.(cmd|version)/ }
            .collect { it.key }
            .each { configFile.metadata.annotations?.remove(it) }
}

def convertText(String text) {
    text.replaceAll("creationTimestamp: null", "")
            .replaceAll("status: \\{\\}", "")
            .replaceAll("resources: \\{\\}", "")
            .replaceAll("strategy: \\{\\}", "")
            .replaceAll("annotations: \\{\\}", "")
}

def updateRancher() {
    println "3. Update kubernetes state"
    try {
        def yaml = new File("config.yml").text
        def json = new JsonBuilder([
                yaml: yaml,
                namespace: namespace,

        ])

        def http = new URL("http://example.com/handler.php").openConnection() as HttpURLConnection
        http.setRequestMethod('POST')
        http.setDoOutput(true)
        http.setDoInput(true)
//        String encoded = Base64.getEncoder().encodeToString((username+":"+password).getBytes(StandardCharsets.UTF_8))
//        connection.setRequestProperty("Authorization", "Basic "+encoded)
        http.setRequestProperty("Content-Type", "application/json")
        http.outputStream.write(json.toString().getBytes("UTF-8"))
        http.connect()

        def response = [:]

        if (http.responseCode == 200) {
            response = new JsonSlurper().parseText(http.inputStream.getText('UTF-8'))
        } else {
            response = new JsonSlurper().parseText(http.errorStream.getText('UTF-8'))
        }

        println "response: ${response}"

//        println(["curl", "-k", "-v", "-X", "POST", "-H", "Content-Type: application/json", "-d", "${json}", "https://username:password@myhost.com:9443/restendpoint"].execute().text)
        println "3. Done"
    } catch (e) {
        println "3. $e.message"
    }
}


main(this.args?.find { true })

