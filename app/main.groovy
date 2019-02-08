import groovy.io.FileType
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

import java.util.concurrent.atomic.AtomicInteger

import static Config.*

class Config {

    public static String NAMESPACE = System.getenv('K8S_NAMESPACE') ?: System.getenv('CI_COMMIT_REF_NAME')
    public static String RANCHER_BEARER = System.getenv('RANCHER_BEARER')
    public static String RANCHER_URL = System.getenv('RANCHER_URL') ?: "https://rancher-test.test.164.org"
    public static String CONFIGURED = "configured"
    public static String CREATED = "created"
    public static Long QUEUE_TIMEOUT = Long.parseLong(System.getenv('QUEUE_TIMEOUT') ?: "1000")

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
    println "0. Prepare to work with namespace '${NAMESPACE}' at ${new File('.').absolutePath}"
    if (arg ==~ /--nginx|-n/) {
        generateConfigForLocalDev(arg)
    } else {
        convertCompose(arg)
    }
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
        def file = new File("docker-compose.yml")
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
        System.err.println("1. ERROR: $e.message")
    }
}

def generateConfigForLocalDev(String arg) {
    DumperOptions options = new DumperOptions()
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK)
    Yaml yaml = new Yaml(options)
    def nginxConf = []
    def files = []
    def currentDir = new File('.')
    currentDir.eachFileMatch(FileType.ANY, ~/.*yaml/) {
        files << it
    }
    files
        .findAll { it.name =~ /ingress/ }
        .sort { first, second ->
            first.name <=> second.name
        }
        .each {
            nginxConf << generateNginxConf(yaml.load(convertText(it.text)))
        }

    new File("default.conf").text = nginxConf.join('')
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

    println "   Found ${currentDir.listFiles().findAll { it.name ==~ /.*yaml/ }.size()} yaml files"
    def files = []
    currentDir.eachFileMatch(FileType.ANY, ~/.*yaml/) {
        files << it
    }

    def deploymentsCount = files.size / files.findAll { it.name =~ /deployment/ }.collect().size()
    def prioritiesCount = Math.round(files.size / deploymentsCount)

    println "   Generate priorities for ${prioritiesCount} deployments"
    int counter = 1
    (1..prioritiesCount).each {
        output << generatePriorityClass(it)
        println "   Work with ${counter++} priority-${it}.yaml"
    }

    AtomicInteger indexCounter = new AtomicInteger(0)
    files.sort { first, second ->
        first.name <=> second.name
    }
    .each {
        println "   Work with ${counter++} $it.name"
        def configFile = yaml.load(convertText(it.text))
        convertYaml(configFile, indexCounter)
        output << yaml.dump(configFile)
    }

    println "   Total number of files for deploy is ${Math.round(output.size / deploymentsCount)}"
    outputFile.text = output.join('---\n')
    if (arg ==~ /--clean|-c/) {
        println "   Clean kubernetes configuration files"
        new File('.').eachFileMatch(FileType.ANY, ~/.*yaml/) { it.delete() }
    }

    println "2. Done"
}

private List<Object> convertYaml(def configFile, def index) {
    configFile.metadata.annotations
            .findAll { it.key ==~ /kompose\.(cmd|version|image-pull-secret|service\.expose|service\.type)/ }
            .collect { it.key }
            .each {
        configFile.metadata.annotations?.remove(it)
    }

    def backendPath = findAnnotationInYaml(configFile, /kompose\.backendPath/)
    def healthPath = findAnnotationInYaml(configFile, /kompose\.healthPath/)

    if (configFile.kind == 'Ingress') {
        configFile.spec.rules.each {
            it.http.paths.each {
                it.put('path', backendPath)
            }
        }
    }

    if (configFile.kind == 'Deployment') {
        configFile.spec.template.spec.put('priorityClassName', "priority-${index.incrementAndGet()}".toString())
        configFile.spec.put('backoffLimit', 3)
        configFile.spec.put('terminationGracePeriodSeconds', 30)
        configFile.spec.put('progressDeadlineSeconds', 600)
        configFile.spec.put('strategy', [
                type: 'RollingUpdate',
                rollingUpdate: [
                    maxUnavailable: '50%',
                    maxSurge: '50%'
                ]])


        if (healthPath) {
            configFile.spec.template.spec.containers.each {
                it.put('readinessProbe', [
                        httpGet            : [
                                path: healthPath,
                                port: 8080
                        ],
                        initialDelaySeconds: 300,
                        timeoutSeconds     : 10,
                        periodSeconds      : 30
                ])
                it.put('livenessProbe', [
                        httpGet            : [
                                path: healthPath,
                                port: 8080
                        ],
                        initialDelaySeconds: 30,
                        timeoutSeconds     : 10,
                        periodSeconds      : 30
                ])
            }
        }
    }

}

private String findAnnotationInYaml(configFile, pattern) {
    def output = null
    configFile.metadata.annotations
            .findAll { it.key ==~ pattern }
            .each {
        output = it.value
        configFile.metadata.annotations?.remove(it.key)
    }

    output
}

def convertText(String text) {
    text.replaceAll("creationTimestamp: null", "")
            .replaceAll("status: \\{\\}", "")
            .replaceAll("resources: \\{\\}", "")
            .replaceAll("strategy: \\{\\}", "")
            .replaceAll("annotations: \\{\\}", "")
}

def generateNginxConf(def configFile) {
    def output = """
    location ^~ ${configFile.metadata.annotations.'kompose.backendPath'}/ {
        proxy_set_header        Host \$host:\$server_port;
        proxy_set_header        X-Real-IP \$remote_addr;
        proxy_set_header        X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header        X-Forwarded-Proto \$scheme;

        set \$target http://${configFile.metadata.annotations.'kompose.backendPath'}:8080/;
        proxy_pass \$target;
        proxy_read_timeout      90;

        proxy_intercept_errors on;
        error_page 301 302 307 = @handle_redirect;
    }
"""
}

def generatePriorityClass(def index) {
    def output = """apiVersion: scheduling.k8s.io/v1beta1
kind: PriorityClass
metadata:
  name: priority-$index
value: $index
globalDefault: false
"""
}

def updateRancher() {
    println "3. Update kubernetes state"
    try {
        def yaml = new File("config.yml").text

        long counter = 0
        long collectionCounter = 0
        yaml.split("---")
                .collate(3)
                .each { collection ->

		            def document = collection.join('---')
		            collectionCounter++

		            def json = new JsonBuilder([
		                    yaml     : document,
		                    namespace: NAMESPACE,
		            ])

		            def response = makeHttpRequest(json)

		            println "   response: $collectionCounter ${response}"
		            def sleepTime = 0

		            def stringResult = String.valueOf(response).replaceAll("\\n", "")
		            int changesCount = stringResult.length() - stringResult
		                    .replaceAll(CONFIGURED, "")
		                    .replaceAll(CREATED, "")
		                    .length()
		            def priorityDeploy = stringResult.contains("priorityclass.scheduling.k8s.io")

		            // If changesCount == 0 then, we don't await any action after.
		            // Human doesn't changed yml == we don't wait
		            if (changesCount == 0 || priorityDeploy) {
		                sleepTime = sleepTime
		            } else {
		                counter++
		                sleepTime = Math.min(counter * QUEUE_TIMEOUT, 120_000L)
		            }
		            Thread.sleep(sleepTime)
		            println "   wait $sleepTime done"

		        }

        println "3. Done"
    } catch (e) {
        System.err.println("3. ERROR: $e.message")
    }
}

def makeHttpRequest(JsonBuilder json) {
    def http = new URL("${RANCHER_URL}/v3/clusters/local?action=importYaml").openConnection() as HttpURLConnection
    http.setRequestMethod('POST')
    http.setDoOutput(true)
    http.setDoInput(true)
    http.setRequestProperty("Content-Type", "application/json")
    http.setRequestProperty("Authorization", "Bearer ${RANCHER_BEARER}")
    http.outputStream.write(json.toString().getBytes("UTF-8"))
    http.connect()

    def response = [:]

    if (http.responseCode == 200) {
        response = new JsonSlurper().parseText(http.inputStream.getText('UTF-8'))
    } else {
        response = new JsonSlurper().parseText(http.errorStream.getText('UTF-8'))
    }

    response
}


main(this.args?.find { true })
