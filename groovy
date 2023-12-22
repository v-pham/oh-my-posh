import groovy.io.FileType
import groovy.transform.SourceURI
import java.nio.file.Path
import java.nio.file.Paths
import org.codehaus.groovy.runtime.StackTraceUtils

class ScriptSourceUri {
    @SourceURI
    static URI uri
}

def utilName(){
  return this.getClass().getSimpleName();
}


def throwException(String method, String message){
  def messagePrefix = this.utilName()
  if(method){
    messagePrefix = "${messagePrefix}.${method}"
  }
  throw new Exception("[${messagePrefix}] ERROR: ${message}");
}

def throwException(String message){
  this.throwException('', message);
}

def printMessage(String method, String messageType, String message){
  def messagePrefix = this.utilName()
  if(method){
    messagePrefix = "${messagePrefix}.${method}"
  }
  switch(messageType.toLowerCase()){
    case 'v':
    case 'verbose':
      messageType = 'VERBOSE'
      break;
    case 'd':
    case 'debug':
      messageType = 'DEBUG'
      break;
    case 'e':
    case 'err':
    case 'error':
    case 'w':
    case 'warn':
    case 'warning':
      messageType = 'WARN'
      break;
    default:
      messageType = 'INFO'
      break;
  }
  println("${messagePrefix} ${messageType}: ${message}")
  return;
}

def printMessage(String messageType, String message){
  return this.printMessage('', messageType, message);
}

def printMessage(String message){
  return this.printMessage('', 'info', message);
}

@NonCPS
def initMaps() {
  def sharedMaps = [
    'properties': [
      'rootKey': [
        'name': '',
        'resourcePath': 'sharedmap',
        'isLeaf': false
      ],
      'leafKey': [
        'name': '',
        'resourcePath': 'sharedmap/leafs',
        'isLeaf': true,
        'keyPath': '',
        'rootless': true
      ]
    ],
    'key': [:]
  ]
  def Path scriptLocation = Paths.get(ScriptSourceUri.uri)
  ['rootKey','leafKey'].each { keyType ->
    new File(scriptLocation.getParent().getParent().resolve("resources/${sharedMaps.properties[keyType].resourcePath}").toString()).eachFile (FileType.FILES) { file ->
      def key = sharedMaps.properties[keyType].clone()
      key.resourcePath = "${key.resourcePath}/${file.name}"
      if(keyType == 'leafKey'){
        key.name = file.name.tokenize('.')[-2]
        key.keyPath = file.name[0..<file.name.lastIndexOf('.')]
        if(sharedMaps.key.containsKey(file.name.tokenize('.')[0])){
          key.rootless = false
          sharedMaps.key[file.name.tokenize('.')[0]].leafKeys.add(key.name)
        }
        sharedMaps.key.put(key.name, key)
      }else{
        def keyname = file.name[0..<file.name.lastIndexOf('.')]
        key.leafKeys = new ArrayList()
        key.alias = Eval.me("def mapDef = ${libraryResource(key.resourcePath)}; if(mapDef.containsKey('alias')){ return mapDef.alias }else{ return new ArrayList() };")
        if(!key.alias.contains(keyname)){
          key.alias.add(keyname)
        }
        key.alias = key.alias.collect{ it.toLowerCase() }.unique()
        key.alias.each {
          sharedMaps.key.put(it, key.clone())
          sharedMaps.key[it].name = it
        }
      }
    }
  }
  return sharedMaps.key;
}

@NonCPS
def initMap(String mapName) {
  def sharedMaps = this.initMaps()
  if(sharedMaps.containsKey(mapName) && sharedMaps[mapName].isLeaf){
    mapName = sharedMaps[mapName].keyPath.tokenize('.')[0]
  }else if(!sharedMaps.containsKey(mapName)){
    if(sharedMaps.containsKey(mapName.toLowerCase())){
      mapName = mapName.toLowerCase()
    }else{
      this.throwException('initMap', "Map not found: ${mapName}")
    }
  }
  def mapObject = [
    alias: [],
    definition: [:]
  ]
  def mapDef = [:]
  mapDef['string'] = libraryResource(sharedMaps[mapName].resourcePath)
  mapDef['object'] = Eval.me("def mapDef = ${mapDef.string}; return mapDef;")
  mapDef['mapObject'] = mapObject.clone()
  try {
    if(mapDef.object.containsKey('definition')){
      if(mapDef.object.containsKey('postProcess')){
        mapDef.mapObject = Eval.me("def mapDef = ${mapDef.string}; ${mapDef.object.postProcess}; return mapDef;")
      }
      if(!mapDef.mapObject.containsKey('definition')){
        this.throwException('initMap', "Failed creating shared map definition for: ${mapName} (${filename})")
      }
    }else{
      mapDef.mapObject.definition = mapDef.object
    }
  }catch(Exception err){
    this.throwException('initMap', "An error was caught while importing shared map definition.")
  }
  return mapDef.mapObject.definition;
}

def getSharedMap(String keyName) {
  return this.initMap(keyName);
}

def getCommonMap(String keyName) {
  return this.initMap(keyName);
}

def getParamValue(ArrayList paramNameList, String property, Boolean throwError){
  def paramName = params.keySet().find { paramNameList.contains(it) }
  def paramMap = [
    'name': paramName,
    'value': params[paramName]
  ]
  if(paramName){
    return paramMap;
  }else {
    def errorMessage = "Verify value is defined as one of the following job parameters: ${paramNameList.join(", ")}"
    if(pName){
      errorMessage = "${property} could not be resolved. ${errorMessage}"
    }
    if(throwError){
      this.throwException(errorMessage);
    }else{
      this.printMessage('error', errorMessage);
    }
  }
  return;
}

def getParamValue(ArrayList paramNameList, Boolean throwError){
  def String property = StackTraceUtils.sanitize(new Throwable()).stackTrace[3].methodName
  property = property.toLowerCase().replace('get','')
  return this.getParamValue(paramNameList, property, throwError);
}

def getAppName(Boolean returnError = false) {
  def paramNames = ['CUSTSVC_APPNAME', 'CRIS_APPNAME', 'CUSTSVC_APP', 'CRIS_APP', 'APPNAME', 'DEPLOYMENT']
  appNameParam = this.getParamValue(paramNames, returnError);
  return appNameParam.value;
}

def getAppProperties(String appName = this.getAppName()) {
  if(appName){
    appName = appName.toString().toLowerCase().trim()
  }else{
    appName = 'all'
  }
  def crisApp = this.initMap('apps')
  if(['all','*'].contains(appName)){
    return crisApp;
  }else if(crisApp.containsKey(appName)){
    return crisApp[appName];
  }else{
    this.throwException("AppName not found in APPS: ${appName}");
  }
}

def getAppProperty(String appName, String propertyName) {
  propertyName = propertyName.toString().toLowerCase().trim()
  def appProperties = this.getAppProperties(appName);
  if(appProperties.containsKey(propertyName)){
    return appProperties[propertyName];
  }else {
    this.throwException("Property not defined for \"${appName}\": ${propertyName}");
  }
}

def getAppProperty(String propertyName) {
  return this.getAppProperty(this.getAppName(), propertyName);
}

def setEnvParamMap(mapEnv, propertyName, paramName, outputOption) {
  if(env[paramName]) {
    mapEnv.put(propertyName,env[paramName])
  }else{
    mapEnv.put(propertyName,'false')
  }
  if(['true','false'].contains(mapEnv[propertyName].toString().toLowerCase().trim()) || (mapEnv[propertyName].toString().toLowerCase().trim().length() == 0)){
    mapEnv[propertyName] = mapEnv[propertyName].toBoolean()
  }
  if(['debug','print','verbose'].contains(outputOption.toLowerCase().trim()) || env.VERBOSE_PIPELINE_OUTPUT || env.VERBOSE){
    this.printMessage("Set ${propertyName} to: ${mapEnv[propertyName]}")
  }
  return;
}

def setEnvParamMap(mapEnv, propertyName, paramName) {
  return this.setEnvParamMap(mapEnv, propertyName, paramName, 'quiet');
}

def setEnvParamMap(mapEnv, propertyName) {
  def paramName = "${propertyName}"
  def keyword = ['confirm', 'force', 'ignore', 'override', 'skip'].find { paramName ==~ /$it.*/ }
  if(keyword){
    paramName = keyword + "_" + paramName.replace(keyword, '')
  }
  paramName = paramName.toUpperCase()
  return this.setEnvParamMap(mapEnv, propertyName, paramName, 'quiet');
}

def isCrisEnv(targetEnv, crisEnv) {
  targetEnv = targetEnv.toString().toLowerCase().trim()
  def booleanReturn
  switch(crisEnv.getClass()){
    case java.util.ArrayList:
      crisEnv = crisEnv.collect { it.toLowerCase().trim() }
      booleanReturn = crisEnv.contains(targetEnv)
      break;
    default:
      booleanReturn = crisEnv.toString().toLowerCase().trim() == targetEnv
      break;
  }
  return booleanReturn;
}

def isCrisEnv(crisEnv) {
  def envVars = ['CUSTSVC_ENV', 'CRIS_ENV', 'TARGET_ENV']
  def targetEnvParam = this.getParamValue(envVars, 'crisEnv', true)
  return this.isCrisEnv(targetEnvParam.value, crisEnv);
}

def isCrisEnvNot(targetEnv, crisEnv) {
  return !this.isCrisEnv(targetEnv, crisEnv);
}

def isCrisEnvNot(crisEnv) {
  return !this.isCrisEnv(crisEnv);
}

def getNamespaceProperties(String crisEnv, String byCluster){
  crisEnv = crisEnv.toString().toLowerCase().replace('custsvc-','').replace('develop','dev').trim()
  def crisEnvProperty = [
    'isBaseEnv': true,
    'number': 0
  ]
  def returnOutput
  def crisNamespaces = this.initMap('namespaces')
  crisNamespaces.remove('eksCluster')
  if(['bycluster','cluster','ekscluster','true'].contains(byCluster.toString().toLowerCase().trim())){
    crisNamespaces = crisNamespaces.findAll { it.value.cluster == crisEnv }.collect { it }
  }else{
    if(crisEnv ==~ /.*ext.*/){
      crisEnv = crisEnv.replace('ext','').replace('-','')
      crisEnv = "ext-${crisEnv}"
      crisEnvProperty['env'] = "${crisEnv}"
      if(crisEnv ==~ /.*(dt|stage)\d+/){
        crisEnvProperty.isBaseEnv = false
        crisEnvProperty.number = crisEnv.replaceAll("[^0-9]", "")
        crisEnv = crisEnv.replaceAll("[0-9]", "")
      }
    }
  }
  if(crisEnv == 'all' || crisEnv == ''){
    returnOutput = crisNamespaces;
  }else if(crisNamespaces.containsKey(crisEnv)){
    returnOutput = crisNamespaces[crisEnv]
  }
  if(returnOutput){
    return returnOutput;
  }else{
    this.throwException('getNamespaceProperties', "Unable to match argument to any CRIS namespace(s): ${crisEnvProperty.envName}")
  }
}

def getNamespaceProperties(String crisEnv){
  crisEnv = crisEnv.toString().toLowerCase().trim()
  def byCluster = 'false'
  if(['nonprod','prod-ext'].contains(crisEnv)){
    byCluster = 'true'
  }
  return this.getNamespaceProperties(crisEnv, byCluster);
}

def getNamespaces(String crisEnv, String byCluster){
  crisEnv = crisEnv.toString().toLowerCase().replace('custsvc-','').replace('develop','dev').trim()
  byCluster = byCluster.toString().toLowerCase().trim()
  def returnOutput
  def crisNamespaces = this.initMap('namespaces')
  try {
    if(['bycluster','cluster','ekscluster','true'].contains(byCluster)){
      if(crisNamespaces.eksCluster.containsKey(crisEnv)){
        returnOutput = crisNamespaces.eksCluster[crisEnv]
      }else{
        throw new Exception()
      }
    }else{
      if(crisNamespaces.containsKey(crisEnv) && crisNamespaces[crisEnv].containsKey('namespaces')){
        returnOutput = crisNamespaces[crisEnv].namespaces
      }else{
        throw new Exception()
      }
    }
  }catch(Exception err){
    def utilName = this.utilName()
    throw new Exception("[$utilName] ERROR: No CRIS namespace(s) could be returned: ${crisEnv}")
  }
  return returnOutput;
}

def getNamespaces(String crisEnv){
  crisEnv = crisEnv.toString().toLowerCase().replace('custsvc-','').replace('develop','dev').trim()
  def byCluster = 'false'
  def namespaces
  if(crisEnv ==~ /custsvc-.*/){
    crisEnv = crisEnv.replace('custsvc-','')
  }else if(['nonprod','prod-ext'].contains(crisEnv)){
    byCluster = 'true'
  }
  namespaces = this.getNamespaces(crisEnv, byCluster)
  return namespaces;
}

def getNamespace(String crisEnv){
  def properties = this.getNamespaceProperties(crisEnv, 'false')
  return properties.namespace;
}

return this;
