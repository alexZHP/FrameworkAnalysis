Spring 源码阅读笔记

启动代码
```
ApplicationContext applicationContext = new ClassPathXmlApplicationContext(new String[] {"spring-config.xml"}, true, null);
HelloService helloService = (HelloService) applicationContext.getBean("helloService");
helloService.sayHello();
```
附上一份ClassPathXmlApplicationContext类结构图:  
![ClassPathXmlApplicationContext类结构图](images/ClassPathXmlApplicationContext类结构图.png) 这张图后续将多次提到  

先看一下`ClassPathXmlApplicationContext`的构造方法
```
public ClassPathXmlApplicationContext(
        String[] configLocations, boolean refresh, @Nullable ApplicationContext parent)
        throws BeansException {

    // 这里虽然不重要,但最好稍微关注下这些继承关系,能够方便后续确定一些模板方法模式具体进了那个类的实现中,还能让代码理解的更轻松一点
    super(parent);

    // 这里的主要作用是设置AbstractRefreshableConfigApplicationContext类中的(String[] )configLocations属性
    setConfigLocations(configLocations);
    if (refresh) {
        // 这里是核心点
        refresh();
    }
}
```
先看一下`super(parent);`, 如果你跟下去就会发现它途经`AbstractXmlApplicationContext`、`AbstractRefreshableConfigApplicationContext`、`AbstractRefreshableApplicationContext`直到`AbstractApplicationContext`类(按照结构图不断上溯)。

以下是`AbstractApplicationContext`中的代码调用
```
/**
 * Create a new AbstractApplicationContext with no parent.
 */
public AbstractApplicationContext() {
    // 记住这个东西就行,后续会用到。 以防后面不知所措  这里是通过下面那个构造函数的this();进入的
    this.resourcePatternResolver = getResourcePatternResolver();
}

/**
 * Create a new AbstractApplicationContext with the given parent context.
 * @param parent the parent context
 */
public AbstractApplicationContext(@Nullable ApplicationContext parent) {
    this();
    // 这个就不用考虑了,在本文中这里始终为空
    setParent(parent);
}

// AbstractApplicationContext
protected ResourcePatternResolver getResourcePatternResolver() {
    // 这里的 this 在本文中就是 ClassPathXmlApplicationContext 了,后续该类也会被用到
    return new PathMatchingResourcePatternResolver(this);
}
```
我们通过`super(parent);`确定了些什么？
- `ClassPathXmlApplicationContext`类的继承结构
- `resourcePatternResolver`的初始化(默认`PathMatchingResourcePatternResolver`)

通过上面的关系图也可以找到`PathMatchingResourcePatternResolver.class`,这两点都需要记一下,方便后续理解代码。

回到对`ClassPathXmlApplicationContext`构造方法的分析中继续看。  

现在看看`setConfigLocations(configLocations);`做了什么?
- 设置`AbstractRefreshableConfigApplicationContext`类中的`configLocations`属性  

代码如下:
```
// AbstractRefreshableConfigApplicationContext
public void setConfigLocations(@Nullable String... locations) {
    if (locations != null) {
        Assert.noNullElements(locations, "Config locations must not be null");
        this.configLocations = new String[locations.length];
        for (int i = 0; i < locations.length; i++) {
            // 这里
            this.configLocations[i] = resolvePath(locations[i]).trim();
        }
    }
    else {
        this.configLocations = null;
    }
}
```

然后我们开始关注`refresh()`这个核心点  
这里我们从`ClassPathXmlApplicationContext.class`走进了`AbstractApplicationContext.class`
```
// AbstractApplicationContext
public void refresh() throws BeansException, IllegalStateException {
    // 这个是个核心的方法, 后面会将大部分的方法进行分析
    synchronized (this.startupShutdownMonitor) {
        // Prepare this context for refreshing.
        prepareRefresh();

        // Tell the subclass to refresh the internal bean factory.
        ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();

        // Prepare the bean factory for use in this context.
        prepareBeanFactory(beanFactory);

        try {
            // Allows post-processing of the bean factory in context subclasses.
            postProcessBeanFactory(beanFactory);

            // Invoke factory processors registered as beans in the context.
            invokeBeanFactoryPostProcessors(beanFactory);

            // Register bean processors that intercept bean creation.
            registerBeanPostProcessors(beanFactory);

            // Initialize message source for this context.
            initMessageSource();

            // Initialize event multicaster for this context.
            initApplicationEventMulticaster();

            // Initialize other special beans in specific context subclasses.
            onRefresh();

            // Check for listener beans and register them.
            registerListeners();

            // Instantiate all remaining (non-lazy-init) singletons.
            finishBeanFactoryInitialization(beanFactory);

            // Last step: publish corresponding event.
            finishRefresh();
        }

        catch (BeansException ex) {
            if (logger.isWarnEnabled()) {
                logger.warn("Exception encountered during context initialization - " +
                        "cancelling refresh attempt: " + ex);
            }

            // Destroy already created singletons to avoid dangling resources.
            destroyBeans();

            // Reset 'active' flag.
            cancelRefresh(ex);

            // Propagate exception to caller.
            throw ex;
        }

        finally {
            // Reset common introspection caches in Spring's core, since we
            // might not ever need metadata for singleton beans anymore...
            resetCommonCaches();
        }
    }
}
```
一行行的分析下这个方法, 理解以下Spring到底搞了些什么。  
这里先附上一份PropertyResolver结构图  
![PropertyResolver结构图](images/PropertyResolver结构图.png)  

先看`org.springframework.context.support.AbstractApplicationContext#prepareRefresh()`
```
// AbstractApplicationContext(表示内容位于该类中, 如果有特殊的将会在方法上标明)

protected void prepareRefresh() {
    // ... 省略部分再分析中不紧要的代码, 后续的'...'都代表这个意思

    // 这里在我这个版本(5.1.4)的源码中没有具体内容,也没有子类实现
    // Initialize any placeholder property sources in the context environment
    initPropertySources();

    // 看下这行代码, 下面贴出了'getEnvironment()'的代码和分析
    // Validate that all properties marked as required are resolvable
    // see ConfigurablePropertyResolver#setRequiredProperties
    getEnvironment().validateRequiredProperties();

    // 初始化一个empty的事件集合
    // Allow for the collection of early ApplicationEvents,
    // to be published once the multicaster is available...
    this.earlyApplicationEvents = new LinkedHashSet<>();
}
```
我们分析以下`getEnvironment().validateRequiredProperties();`的调用。  


```
// AbstractApplicationContext

// 首先我们看下`getEnviorment()`方法

// 创建了一个标准环境(StandardEnvironment)实例, 将其赋予本类的environment变量。
// 这个方法在后续也有再次调用
@Override
public ConfigurableEnvironment getEnvironment() {
    if (this.environment == null) {
        this.environment = createEnvironment();
    }
    return this.environment;
}

/**
 * Create and return a new {@link StandardEnvironment}.
 * <p>Subclasses may override this method in order to supply
 * a custom {@link ConfigurableEnvironment} implementation.
 */
protected ConfigurableEnvironment createEnvironment() {
    return new StandardEnvironment();
}

// 然后就是`validateRequiredProperties()`方法  
// 通过上面的结构图,我们能跟进到`org.springframework.core.env.AbstractEnvironment#validateRequiredProperties()`中

// AbstractEnvironment 

private final ConfigurablePropertyResolver propertyResolver =
            new PropertySourcesPropertyResolver(this.propertySources);

@Override
public void validateRequiredProperties() throws MissingRequiredPropertiesException {
    // 这里的 propertyResolver 是 AbstractEnvironment 默认的初始化的,
    // 使用的是 PropertySourcesPropertyResolver
    this.propertyResolver.validateRequiredProperties();
}
```
在图中你可以找到`PropertySourcesPropertyResolver`  
这里最终进入其父类`AbstractPropertyResolver`中
```
// AbstractPropertyResolver

private final Set<String> requiredProperties = new LinkedHashSet<>();

@Override
public void validateRequiredProperties() {
    // requiredProperties这个东西它在本方法这次调用时间为empty,暂未研究何时会有值
    // 这些以后研究到再补上

    MissingRequiredPropertiesException ex = new MissingRequiredPropertiesException();
    for (String key : this.requiredProperties) {
        if (this.getProperty(key) == null) {
            ex.addMissingRequiredProperty(key);
        }
    }
    if (!ex.getMissingRequiredProperties().isEmpty()) {
        throw ex;
    }
}
```
看到这里发现`prepareRefresh()`在调用中,核心的作用是创建了一个`Environment`并赋值给`AbstractApplicationContext`类的`environment`属性

然后第二行代码`obtainFreshBeanFactory()`分析开始

```
// AbstractApplicationContext
protected ConfigurableListableBeanFactory obtainFreshBeanFactory() {
    // 这个地方是关键
    refreshBeanFactory();
    // 这个是个钩子,去子类中寻找实现。其最终目的是通过线程安全的方式获取到beanFactory属性
    // 由图可以看出使用的是AbstractRefreshableApplicationContext类的实现
    return getBeanFactory(); 
}
```
根据类结构图,我们跟进到`AbstractRefreshableApplicationContext#refreshBeanFactory()`中
```
// AbstractRefreshableApplicationContext

@Override
protected final void refreshBeanFactory() throws BeansException {
    if (hasBeanFactory()) {
        // 如果已经有BeanFactory了 -> 销毁。
        destroyBeans();
        closeBeanFactory();
    }
    try {
        // 这里我们看名字也知道是创建一个新的bean factory,下面贴出代码。
        DefaultListableBeanFactory beanFactory = createBeanFactory();
        // 下面这2行对 beanFactory 设置了一些值
        beanFactory.setSerializationId(getId());
        customizeBeanFactory(beanFactory);
        // 这个是核心点
        loadBeanDefinitions(beanFactory);
        synchronized (this.beanFactoryMonitor) {
            this.beanFactory = beanFactory;
        }
    }
    catch (IOException ex) {
        throw new ApplicationContextException("I/O error parsing bean definition source for " + getDisplayName(), ex);
    }
}

protected DefaultListableBeanFactory createBeanFactory() {
    // getInternalParentBeanFactory() 理解为null就行了, 我没去深入理解。
    // 默认使用 DefaultListableBeanFactory 类。
    return new DefaultListableBeanFactory(getInternalParentBeanFactory());
}
```
跨过对`beanFactory`的属性设置,我们转进到`loadBeanDefinitions(DefaultListableBeanFactory)`看一下。
```
// AbstractXmlApplicationContext

@Override
protected void loadBeanDefinitions(DefaultListableBeanFactory beanFactory) throws BeansException, IOException {
    // 这里你需要留意以下 XmlBeanDefinitionReader 的构造方法'XmlBeanDefinitionReader(BeanDefinitionRegistry)',下面贴了下代码
    // beanFactory 参数 是 之前创建的 DefaultListableBeanFactory 实例
    XmlBeanDefinitionReader beanDefinitionReader = new XmlBeanDefinitionReader(beanFactory);

    // 这里的 this 都是指 ClassPathXmlApplicationContext 的实例
    // this.getEnvironment() 获取上面 AbstractApplicationContext 类中的 enviroment
    beanDefinitionReader.setEnvironment(this.getEnvironment()); 
    // 通过 ClassPathXmlApplicationContext 的类结构图,可以看到它的一个最上级是 ResourceLoader
    beanDefinitionReader.setResourceLoader(this);
    // ResourceEntityResolver 仅仅对 ResourceLoader 进行了一个包装
    beanDefinitionReader.setEntityResolver(new ResourceEntityResolver(this));

    // 这个方法是对 beanDefinitionReader 设置了一个其他属性  -> reader.setValidating(this.validating); -> [validating 默认为 true]
    initBeanDefinitionReader(beanDefinitionReader);

    // 这个是要点,方法代码放在下面
    loadBeanDefinitions(beanDefinitionReader);
}

protected void loadBeanDefinitions(XmlBeanDefinitionReader reader) throws BeansException, IOException {
    Resource[] configResources = getConfigResources();
    if (configResources != null) {
        reader.loadBeanDefinitions(configResources);
    }
    // 这里获取的就是上面调用 setConfigLocations 方法设置的 configLocations
    String[] configLocations = getConfigLocations();
    if (configLocations != null) {
        // 可以看到是调用的 XmlBeanDefinitionReader 实例的 loadBeanDefinitions 方法,下面继续分析它。
        reader.loadBeanDefinitions(configLocations);
    }
}

// 关于 new XmlBeanDefinitionReader(beanFactory) 的调用过程

// XmlBeanDefinitionReader
public XmlBeanDefinitionReader(BeanDefinitionRegistry registry) {
    super(registry);
}

// AbstractBeanDefinitionReader
protected AbstractBeanDefinitionReader(BeanDefinitionRegistry registry) {
    // 注意这些属性赋值,你最好记下都赋值了什么属性
    Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
    this.registry = registry;

    // Determine ResourceLoader to use.
    if (this.registry instanceof ResourceLoader) {
        this.resourceLoader = (ResourceLoader) this.registry;
    }
    else {
        this.resourceLoader = new PathMatchingResourcePatternResolver();
    }

    // Inherit Environment if possible
    if (this.registry instanceof EnvironmentCapable) {
        this.environment = ((EnvironmentCapable) this.registry).getEnvironment();
    }
    else {
        this.environment = new StandardEnvironment();
    }
}
```
附上一份XmlBeanDefinitionReader类关系结构图  
![XmlBeanDefinitionReader类关系结构图](images/XmlBeanDefinitionReader类关系结构图.png)

跟进到`AbstractBeanDefinitionReader#loadBeanDefinitions`中
```
// AbstractBeanDefinitionReader
public int loadBeanDefinitions(String location, @Nullable Set<Resource> actualResources) throws BeanDefinitionStoreException {
    // 如果你不记得这个是什么了,就往上翻下
    // 这里就是获取到 ClassPathXmlApplicationContext 的实例。 (通过 'beanDefinitionReader.setResourceLoader(this)' 设置的)
    ResourceLoader resourceLoader = getResourceLoader();
    if (resourceLoader == null) {
        throw new BeanDefinitionStoreException(
                "Cannot load bean definitions from location [" + location + "]: no ResourceLoader available");
    }
    // 通过 'ClassPathXmlApplicationContext类结构图' 可以看到 ResourcePatternResolver 也是 ClassPathXmlApplicationContext 的父类型
    if (resourceLoader instanceof ResourcePatternResolver) {
        // Resource pattern matching available.
        try {
            // 在下面稍微分析下这里
            Resource[] resources = ((ResourcePatternResolver) resourceLoader).getResources(location);
            int count = loadBeanDefinitions(resources);
            if (actualResources != null) {
                Collections.addAll(actualResources, resources);
            }
            if (logger.isTraceEnabled()) {
                logger.trace("Loaded " + count + " bean definitions from location pattern [" + location + "]");
            }
            return count;
        }
        catch (IOException ex) {
            throw new BeanDefinitionStoreException(
                    "Could not resolve bean definition resource pattern [" + location + "]", ex);
        }
    }
    else {
        // Can only load single resources by absolute URL.
        Resource resource = resourceLoader.getResource(location);
        int count = loadBeanDefinitions(resource);
        if (actualResources != null) {
            actualResources.add(resource);
        }
        if (logger.isTraceEnabled()) {
            logger.trace("Loaded " + count + " bean definitions from location [" + location + "]");
        }
        return count;
    }
}
```
对`Resource[] resources = ((ResourcePatternResolver) resourceLoader).getResources(location)`分析
```
// 先跟进 AbstractApplicationContext#getResources(String)

// AbstractApplicationContext
@Override
public Resource[] getResources(String locationPattern) throws IOException {
    // 这里的 resourcePatternResolver 上面提到过,不多说,问了就是往上翻
    return this.resourcePatternResolver.getResources(locationPattern);
}

// 再转到 PathMatchingResourcePatternResolver#getResources(String)

// PathMatchingResourcePatternResolver
public Resource[] getResources(String locationPattern) throws IOException {
    Assert.notNull(locationPattern, "Location pattern must not be null");
    if (locationPattern.startsWith(CLASSPATH_ALL_URL_PREFIX)) {
        // 如果是以 classpath*: 开头的进到这里
        // a class path resource (multiple resources for same name possible)
        if (getPathMatcher().isPattern(locationPattern.substring(CLASSPATH_ALL_URL_PREFIX.length()))) {
            // a class path resource pattern
            return findPathMatchingResources(locationPattern);
        }
        else {
            // all class path resources with the given name
            return findAllClassPathResources(locationPattern.substring(CLASSPATH_ALL_URL_PREFIX.length()));
        }
    }
    else {
        // Generally only look for a pattern after a prefix here,
        // and on Tomcat only after the "*/" separator for its "war:" protocol.
        int prefixEnd = (locationPattern.startsWith("war:") ? locationPattern.indexOf("*/") + 1 :
                locationPattern.indexOf(':') + 1);
        if (getPathMatcher().isPattern(locationPattern.substring(prefixEnd))) {
            // a file pattern
            return findPathMatchingResources(locationPattern);
        }
        else {
            // 因为我们的案例是 spring-config.xml 所以最终会进到这里

            // 1.getResourceLoader() 还是 ClassPathXmlApplicationContext 由上面 'new PathMatchingResourcePatternResolver(this)' 这行代码传入
            // 2.根据 'ClassPathXmlApplicationContext类关系图' 可以确认 'getResource(locationPattern)' 进入的是 DefaultResourceLoader#getResource(String)
            return new Resource[] {getResourceLoader().getResource(locationPattern)};
        }
    }
}
```
这里最后看下`DefaultResourceLoader#getResource(String)`的代码
```
// DefaultResourceLoader

@Override
public Resource getResource(String location) {
    Assert.notNull(location, "Location must not be null");

    for (ProtocolResolver protocolResolver : this.protocolResolvers) {
        Resource resource = protocolResolver.resolve(location, this);
        if (resource != null) {
            return resource;
        }
    }

    if (location.startsWith("/")) {
        return getResourceByPath(location);
    }
    else if (location.startsWith(CLASSPATH_URL_PREFIX)) {
        return new ClassPathResource(location.substring(CLASSPATH_URL_PREFIX.length()), getClassLoader());
    }
    else {
        try {
            // Try to parse the location as a URL...
            URL url = new URL(location);
            return (ResourceUtils.isFileURL(url) ? new FileUrlResource(url) : new UrlResource(url));
        }
        catch (MalformedURLException ex) {
            // 最后进到这里了
            return getResourceByPath(location);
        }
    }
}

// 可以看出返回的Resource都是ClassPathContextResource的类实例
protected Resource getResourceByPath(String path) {
    return new ClassPathContextResource(path, getClassLoader());
}

/**
 * ClassPathResource that explicitly expresses a context-relative path
 * through implementing the ContextResource interface.
 */
protected static class ClassPathContextResource extends ClassPathResource implements ContextResource {

    public ClassPathContextResource(String path, @Nullable ClassLoader classLoader) {
        super(path, classLoader);
    }

    @Override
    public String getPathWithinContext() {
        return getPath();
    }

    @Override
    public Resource createRelative(String relativePath) {
        String pathToUse = StringUtils.applyRelativePath(getPath(), relativePath);
        return new ClassPathContextResource(pathToUse, getClassLoader());
    }
}

// ClassPathResource
public ClassPathResource(String path, @Nullable ClassLoader classLoader) {
    Assert.notNull(path, "Path must not be null");
    String pathToUse = StringUtils.cleanPath(path);
    if (pathToUse.startsWith("/")) {
        pathToUse = pathToUse.substring(1);
    }
    this.path = pathToUse;
    this.classLoader = (classLoader != null ? classLoader : ClassUtils.getDefaultClassLoader());
}
```
附上一份ClassPathContextResource类结构关系图  
![ClassPathContextResource类结构关系图](images/ClassPathContextResource类结构关系图.png)

回到`AbstractBeanDefinitionReader#loadBeanDefinitions(String, Set<Resource>)`中看下一行`loadBeanDefinitions(Resource[])`  
这里会真正进入到`XmlBeanDefinitionReader.class`中,具体过程不贴了,只贴最终位置
```
// XmlBeanDefinitionReader

// 这里的EncodedResource我暂时理解的就是Resource的一个装饰者...
public int loadBeanDefinitions(EncodedResource encodedResource) throws BeanDefinitionStoreException {
    // ...

    try {
        // encodedResource.getResource() 操作的还是原来的 Resource, 就是上面的创建的 ClassPathContextResource 实例
        InputStream inputStream = encodedResource.getResource().getInputStream();
        try {
            InputSource inputSource = new InputSource(inputStream);
            if (encodedResource.getEncoding() != null) {
                inputSource.setEncoding(encodedResource.getEncoding());
            }
            // 这里是重点
            return doLoadBeanDefinitions(inputSource, encodedResource.getResource());
        }
        finally {
            inputStream.close();
        }
    }
    catch (IOException ex) {
        throw new BeanDefinitionStoreException(
                "IOException parsing XML document from " + encodedResource.getResource(), ex);
    }
    finally {
        currentResources.remove(encodedResource);
        if (currentResources.isEmpty()) {
            this.resourcesCurrentlyBeingLoaded.remove();
        }
    }
}

protected int doLoadBeanDefinitions(InputSource inputSource, Resource resource)
            throws BeanDefinitionStoreException {

    try {
        // 这一步就是将资源解析成 Document 对象
        Document doc = doLoadDocument(inputSource, resource);
        // 关注点在这.
        int count = registerBeanDefinitions(doc, resource);
        if (logger.isDebugEnabled()) {
            logger.debug("Loaded " + count + " bean definitions from " + resource);
        }
        return count;
    }

    // ...
}

public int registerBeanDefinitions(Document doc, Resource resource) throws BeanDefinitionStoreException {
    // 获取到一个 DefaultBeanDefinitionDocumentReader 的实例。
    // BeanDefinitionDocumentReader 接口中 只有一个 registerBeanDefinitions(Document, XmlReaderContext) 方法
    BeanDefinitionDocumentReader documentReader = createBeanDefinitionDocumentReader();
    int countBefore = getRegistry().getBeanDefinitionCount();
    
    // 这个是关键点。先看createReaderContext方法的代码, 返回一个 XmlReaderContext 的实例
    documentReader.registerBeanDefinitions(doc, createReaderContext(resource));
    return getRegistry().getBeanDefinitionCount() - countBefore;
}

private Class<? extends BeanDefinitionDocumentReader> documentReaderClass = DefaultBeanDefinitionDocumentReader.class;

protected BeanDefinitionDocumentReader createBeanDefinitionDocumentReader() {
    return BeanUtils.instantiateClass(this.documentReaderClass);
}


public XmlReaderContext createReaderContext(Resource resource) {
    // 这里需要关注以下调用传入参数,基本是默认值  主要记一下的内容 getNamespaceHandlerResolver() 
    // 构造方法就不贴了,有兴趣自己看看
    return new XmlReaderContext(resource, this.problemReporter, this.eventListener,
            this.sourceExtractor, this, getNamespaceHandlerResolver());
}

// 返回一个 DefaultNamespaceHandlerResolver 的实例
public NamespaceHandlerResolver getNamespaceHandlerResolver() {
    if (this.namespaceHandlerResolver == null) {
        this.namespaceHandlerResolver = createDefaultNamespaceHandlerResolver();
    }
    return this.namespaceHandlerResolver;
}

protected NamespaceHandlerResolver createDefaultNamespaceHandlerResolver() {
    ClassLoader cl = (getResourceLoader() != null ? getResourceLoader().getClassLoader() : getBeanClassLoader());
    return new DefaultNamespaceHandlerResolver(cl);
}
```
我们回到上面的`documentReader.registerBeanDefinitions`方法继续分析  
```
// DefaultBeanDefinitionDocumentReader

public void registerBeanDefinitions(Document doc, XmlReaderContext readerContext) {
    // 一个赋值操作
    this.readerContext = readerContext;
    doRegisterBeanDefinitions(doc.getDocumentElement());
}

protected void doRegisterBeanDefinitions(Element root) {
    // Any nested <beans> elements will cause recursion in this method. In
    // order to propagate and preserve <beans> default-* attributes correctly,
    // keep track of the current (parent) delegate, which may be null. Create
    // the new (child) delegate with a reference to the parent for fallback purposes,
    // then ultimately reset this.delegate back to its original (parent) reference.
    // this behavior emulates a stack of delegates without actually necessitating one.
    BeanDefinitionParserDelegate parent = this.delegate;

    // private BeanDefinitionParserDelegate delegate;

    // getReaderContext() 就是上面 createReaderContext(Resource) 的值  
    this.delegate = createDelegate(getReaderContext(), root, parent);

    // BeanDefinitionParserDelegate 这个类是一个将 xml 元数据解析成 BeanDefinition 的关键, 它持有从xml中读取到的内容

    // defaultNamespace -> http://www.springframework.org/schema/beans
    if (this.delegate.isDefaultNamespace(root)) {

        // 这里进行对 profile 属性的配对, 不了解 profile 的可以自己搜一下。

        String profileSpec = root.getAttribute(PROFILE_ATTRIBUTE); // PROFILE_ATTRIBUTE -> profile
        if (StringUtils.hasText(profileSpec)) {
            // MULTI_VALUE_ATTRIBUTE_DELIMITERS -> ,;
            String[] specifiedProfiles = StringUtils.tokenizeToStringArray(
                    profileSpec, BeanDefinitionParserDelegate.MULTI_VALUE_ATTRIBUTE_DELIMITERS);
            // We cannot use Profiles.of(...) since profile expressions are not supported
            // in XML config. See SPR-12458 for details.
            if (!getReaderContext().getEnvironment().acceptsProfiles(specifiedProfiles)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Skipped XML bean definition file due to specified profiles [" + profileSpec +
                            "] not matching: " + getReaderContext().getResource());
                }
                return;
            }
        }
    }

    // 空实现
    preProcessXml(root);
    // 重点
    parseBeanDefinitions(root, this.delegate);
    // 空实现
    postProcessXml(root);

    this.delegate = parent;
}

protected void parseBeanDefinitions(Element root, BeanDefinitionParserDelegate delegate) {
    // 在这里是对xml文件的解析, 如果你有自己想要的解析方式, 你可以根据这里改进一下
    
    // 如果命名空间是 http://www.springframework.org/schema/beans 则 isDefaultNamespace: true
    if (delegate.isDefaultNamespace(root)) {
        NodeList nl = root.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node node = nl.item(i);
            if (node instanceof Element) {
                Element ele = (Element) node;
                if (delegate.isDefaultNamespace(ele)) {
                    // bean import alias beans 这些标签走这里
                    parseDefaultElement(ele, delegate);
                }
                else {
                    // 例如: context:component-scan、aop-config 就会走这里
                    // 后面顺便看一下 component-scan、和aop-config
                    delegate.parseCustomElement(ele);
                }
            }
        }
    }
    else {
        // 同上面
        delegate.parseCustomElement(root);
    }
}

// 根据 nodeName 决定进入具体操作中
private void parseDefaultElement(Element ele, BeanDefinitionParserDelegate delegate) {
    if (delegate.nodeNameEquals(ele, IMPORT_ELEMENT)) {  // import
        importBeanDefinitionResource(ele);
    }
    else if (delegate.nodeNameEquals(ele, ALIAS_ELEMENT)) { // alias
        processAliasRegistration(ele);
    }
    else if (delegate.nodeNameEquals(ele, BEAN_ELEMENT)) { // bean
        // 我们主要看下这个, 代码贴在下边
        processBeanDefinition(ele, delegate);
    }
    else if (delegate.nodeNameEquals(ele, NESTED_BEANS_ELEMENT)) { // beans
        // recurse
        doRegisterBeanDefinitions(ele);
    }
}

protected void processBeanDefinition(Element ele, BeanDefinitionParserDelegate delegate) {
    // 重点  将Element解析成BeanDefinition
    // 其中的重点是关于 依赖注入的一个要点(就是解析property标签的ref属性, 其实可以不写的, 这里还是跟下熟悉下...)
    // BeanDefinitionHolder 对 BeanDefinition 包装了一下,具体细节不提了 有兴趣自己看
    BeanDefinitionHolder bdHolder = delegate.parseBeanDefinitionElement(ele);
    if (bdHolder != null) {
        // 看名称知方法  我只简单看了下  就是进一步的装饰
        // 1.Decorate based on custom attributes first.
        // 2.Decorate based on custom nested elements.
        bdHolder = delegate.decorateBeanDefinitionIfRequired(ele, bdHolder);
        try {
            // Register the final decorated instance.

            // 作用: 将装饰好的 BeanDefinition 注册到 BeanFactory 的注册表中
            // getReaderContext() 获取的是 XmlReaderContext 如果你还记得上面的代码的话, 不记得你可以搜下 'return new XmlReaderContext'
            // getRegistry() 的方法更简单, 如果你还记得要你们留意下'XmlBeanDefinitionReader 的构造方法'
            // 不记得就搜下前面单引号内的关键字, 然后点进getRegistry()配合查看效果更佳...

            // 最后我们关注最外层的 registerBeanDefinition() , 这个也很简单。 
            // 会调用 DefaultListableBeanFactory#registerBeanDefinition 和 DefaultListableBeanFactory#registerAlias 
            // 其中的过程说简单也简单, 说复杂也复杂  只要记住 上面的我这里写的注释就好了。 就不具体看了
            BeanDefinitionReaderUtils.registerBeanDefinition(bdHolder, getReaderContext().getRegistry());
        }
        catch (BeanDefinitionStoreException ex) {
            getReaderContext().error("Failed to register bean definition with name '" +
                    bdHolder.getBeanName() + "'", ele, ex);
        }
        // 发布注册事件  这个不关注
        // Send registration event.
        getReaderContext().fireComponentRegistered(new BeanComponentDefinition(bdHolder));
    }
}

// 这里贴一下 isDefaultNamespace 方法

// BeanDefinitionParserDelegate
public boolean isDefaultNamespace(@Nullable String namespaceUri) {
    // BEANS_NAMESPACE_URI = http://www.springframework.org/schema/beans
    return (!StringUtils.hasLength(namespaceUri) || BEANS_NAMESPACE_URI.equals(namespaceUri));
}

// BeanDefinitionParserDelegate
public boolean isDefaultNamespace(Node node) {
    return isDefaultNamespace(getNamespaceURI(node));
}
```
开始分析`delegate.parseBeanDefinitionElement(ele)`方法调用过程中的热点代码
```
// BeanDefinitionParserDelegate

@Nullable
public BeanDefinitionHolder parseBeanDefinitionElement(Element ele, @Nullable BeanDefinition containingBean) {
    String id = ele.getAttribute(ID_ATTRIBUTE);
    String nameAttr = ele.getAttribute(NAME_ATTRIBUTE);

    // 处理别名
    List<String> aliases = new ArrayList<>();
    if (StringUtils.hasLength(nameAttr)) {
        String[] nameArr = StringUtils.tokenizeToStringArray(nameAttr, MULTI_VALUE_ATTRIBUTE_DELIMITERS);
        aliases.addAll(Arrays.asList(nameArr));
    }

    String beanName = id;
    if (!StringUtils.hasText(beanName) && !aliases.isEmpty()) {
        // 如果没有id, 从别名里面选一个
        beanName = aliases.remove(0);
        if (logger.isTraceEnabled()) {
            logger.trace("No XML 'id' specified - using '" + beanName +
                    "' as bean name and " + aliases + " as aliases");
        }
    }

    // 检查名称是否唯一(类是理解成spring不允许bean id相同)(我没看 看方法名是这意思)
    if (containingBean == null) {
        checkNameUniqueness(beanName, aliases, ele);
    }

    // 重点。 将 element 解析成 beanDefinition, 下面贴上该代码具体
    AbstractBeanDefinition beanDefinition = parseBeanDefinitionElement(ele, beanName, containingBean);
    if (beanDefinition != null) {
        if (!StringUtils.hasText(beanName)) {
            try {
                if (containingBean != null) {
                    beanName = BeanDefinitionReaderUtils.generateBeanName(
                            beanDefinition, this.readerContext.getRegistry(), true);
                }
                else {
                    beanName = this.readerContext.generateBeanName(beanDefinition);
                    // Register an alias for the plain bean class name, if still possible,
                    // if the generator returned the class name plus a suffix.
                    // This is expected for Spring 1.2/2.0 backwards compatibility.
                    String beanClassName = beanDefinition.getBeanClassName();
                    if (beanClassName != null &&
                            beanName.startsWith(beanClassName) && beanName.length() > beanClassName.length() &&
                            !this.readerContext.getRegistry().isBeanNameInUse(beanClassName)) {
                        aliases.add(beanClassName);
                    }
                }
                if (logger.isTraceEnabled()) {
                    logger.trace("Neither XML 'id' nor 'name' specified - " +
                            "using generated bean name [" + beanName + "]");
                }
            }
            catch (Exception ex) {
                error(ex.getMessage(), ele);
                return null;
            }
        }
        String[] aliasesArray = StringUtils.toStringArray(aliases);
        // 这里用 BeanDefinitionHolder 把 BeanDefinition 包装了起来
        return new BeanDefinitionHolder(beanDefinition, beanName, aliasesArray);
    }

    return null;
}

// 值得一看的方法
@Nullable
public AbstractBeanDefinition parseBeanDefinitionElement(
        Element ele, String beanName, @Nullable BeanDefinition containingBean) {
    // parseState这个是一个"栈"
    this.parseState.push(new BeanEntry(beanName));

    // 获取到 element 元素中的 class 属性
    String className = null;
    if (ele.hasAttribute(CLASS_ATTRIBUTE)) {
        className = ele.getAttribute(CLASS_ATTRIBUTE).trim();
    }
    // 获取到 element 元素中的 parent 属性
    String parent = null;
    if (ele.hasAttribute(PARENT_ATTRIBUTE)) {
        parent = ele.getAttribute(PARENT_ATTRIBUTE);
    }

    try {
        // 开始构建BeanDefinition
        // 这个try里面就是个构建指令集, 经过这些方法处理后会形成最终结果返回

        // 这里和下面会对原来的标签添加一些默认值..
        AbstractBeanDefinition bd = createBeanDefinition(className, parent);

        parseBeanDefinitionAttributes(ele, beanName, containingBean, bd);
        bd.setDescription(DomUtils.getChildElementValueByTagName(ele, DESCRIPTION_ELEMENT));

        parseMetaElements(ele, bd);
        // 这几个看名称也能知道个大概吧..我就不一个个细说了, 会死的
        parseLookupOverrideSubElements(ele, bd.getMethodOverrides());
        parseReplacedMethodSubElements(ele, bd.getMethodOverrides());

        parseConstructorArgElements(ele, bd);
        // 提示: <bean>标签有子标签<property>标签可以使用
        // 这个是用来解析property的,我们主要看这个
        parsePropertyElements(ele, bd);
        parseQualifierElements(ele, bd);

        bd.setResource(this.readerContext.getResource());
        bd.setSource(extractSource(ele));

        // 其他方法可以自己看看, 起码我现在不关注他们
        return bd;
    }
    // ... 一些catch
    finally {
        this.parseState.pop();
    }

    return null;
}

public void parsePropertyElements(Element beanEle, BeanDefinition bd) {
    NodeList nl = beanEle.getChildNodes();
    for (int i = 0; i < nl.getLength(); i++) {
        Node node = nl.item(i);
        if (isCandidateElement(node) && nodeNameEquals(node, PROPERTY_ELEMENT)) {
            parsePropertyElement((Element) node, bd);
        }
    }
}

@Nullable
public Object parsePropertyValue(Element ele, BeanDefinition bd, @Nullable String propertyName) {
    // ...

    boolean hasRefAttribute = ele.hasAttribute(REF_ATTRIBUTE);
    boolean hasValueAttribute = ele.hasAttribute(VALUE_ATTRIBUTE);
    if ((hasRefAttribute && hasValueAttribute) ||
            ((hasRefAttribute || hasValueAttribute) && subElement != null)) {
        error(elementName +
                " is only allowed to contain either 'ref' attribute OR 'value' attribute OR sub-element", ele);
    }

    if (hasRefAttribute) {
        // <property key="" ref="..."/>
        // 这里面就是解析的 ref 属性 解析成 BeanReference 类型, 如果我没记错这个东西是依赖注入的一个点, 当然@Autowired是自动识别的(并不会太过深入,就是大致过一遍而已)
        String refName = ele.getAttribute(REF_ATTRIBUTE);
        if (!StringUtils.hasText(refName)) {
            error(elementName + " contains empty 'ref' attribute", ele);
        }
        RuntimeBeanReference ref = new RuntimeBeanReference(refName);
        ref.setSource(extractSource(ele));
        return ref;
    }
    else if (hasValueAttribute) {
        // 这里解析的是 value 属性 <property key="" value="..."/>
        TypedStringValue valueHolder = new TypedStringValue(ele.getAttribute(VALUE_ATTRIBUTE));
        valueHolder.setSource(extractSource(ele));
        return valueHolder;
    }
    // ... 一些其他的判断
}
```
至此`ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();`分析结束了。  

到这里你会注意到其实 xml 中 `<bean/>` 的解析和注册(**并没有实例化对象和赋值**)已经完成了。  
我们上面说了对于`component-scan`和`aop-config`进行下分析..下面就开始了。  


`component-scan`的说明开始
```
// 首先, 你需要再xml里面配置一个 <context:component-scan base-package="a.b.c"/>

// 在上述代码的基础上代码追踪, 到如下部分截至并对截至部分进行分析

// BeanDefinitionParserDelegate
@Nullable
public BeanDefinition parseCustomElement(Element ele, @Nullable BeanDefinition containingBd) {
    String namespaceUri = getNamespaceURI(ele);
    if (namespaceUri == null) {
        return null;
    }
    // readerContext.getNamespaceHandlerResolver() 回忆下, 前面在'new XmlReaderContext'的时候设置了一个'DefaultNamespaceHandlerResolver'的实例
    // 然后我们跟进 DefaultNamespaceHandlerResolver.class 中, 看下 resolve(..) 方法  下面贴出该方法的代码
    NamespaceHandler handler = this.readerContext.getNamespaceHandlerResolver().resolve(namespaceUri);
    if (handler == null) {
        error("Unable to locate Spring NamespaceHandler for XML schema namespace [" + namespaceUri + "]", ele);
        return null;
    }
    // 看完下面的代码后再回来看一眼
    return handler.parse(ele, new ParserContext(this.readerContext, this, containingBd));
}

这里提醒一下, 看一下 DefaultNamespaceHandlerResolver 的构造方法, 它和如下的属性声明有一定关系
public static final String DEFAULT_HANDLER_MAPPINGS_LOCATION = "META-INF/spring.handlers";
然后你可以自己进入spring.handlers文件看一下 （context、aop、beans中的汇总  甚至包括mvc的（如果你引入mvc的包的话..））

// DefaultNamespaceHandlerResolver
// this.readerContext.getNamespaceHandlerResolver().resolve(namespaceUri) 的内部代码
@Override
@Nullable
public NamespaceHandler resolve(String namespaceUri) {
    // 先看这里 就是 从上面spring.handlers文件中拿到的内容 。
    // 提示'getHandlerMappings()'中内容是通过toString()方法初始化的。 toString()方法可能断点调试不到,你可以通过如下实验验证
    // 对于一个重写了toString()方法的类,在打断点调试的时候,(有的)编译器会开启新线程执行它,你就调试不到。如果正常run的方式运行,不会出现该情况..你可以自己写个类试试验证一下
    Map<String, Object> handlerMappings = getHandlerMappings();
    // 这里获取的 对于 component-scan 来说 是 ContextNamespaceHandler
    Object handlerOrClassName = handlerMappings.get(namespaceUri);
    if (handlerOrClassName == null) {
        return null;
    }
    // NamespaceHandler 这个东西也很重要, 下面就会用到
    else if (handlerOrClassName instanceof NamespaceHandler) {
        return (NamespaceHandler) handlerOrClassName;
    }
    else {
        // 我们默认走的这里
        String className = (String) handlerOrClassName;
        try {
            // 加载并实例化从配置文件中获取的指定类
            Class<?> handlerClass = ClassUtils.forName(className, this.classLoader);
            if (!NamespaceHandler.class.isAssignableFrom(handlerClass)) {
                throw new FatalBeanException("Class [" + className + "] for namespace [" + namespaceUri +
                        "] does not implement the [" + NamespaceHandler.class.getName() + "] interface");
            }
            NamespaceHandler namespaceHandler = (NamespaceHandler) BeanUtils.instantiateClass(handlerClass);
            // 这个地方我们贴一下它内部的代码
            namespaceHandler.init(); 
            handlerMappings.put(namespaceUri, namespaceHandler);
            return namespaceHandler;
        }
        catch (ClassNotFoundException ex) {
            throw new FatalBeanException("Could not find NamespaceHandler class [" + className +
                    "] for namespace [" + namespaceUri + "]", ex);
        }
        catch (LinkageError err) {
            throw new FatalBeanException("Unresolvable class definition for NamespaceHandler class [" +
                    className + "] for namespace [" + namespaceUri + "]", err);
        }
    }
}


// namespaceHandler.init() 的内部代码
public class ContextNamespaceHandler extends NamespaceHandlerSupport {

    @Override
    public void init() {
        registerBeanDefinitionParser("property-placeholder", new PropertyPlaceholderBeanDefinitionParser());
        registerBeanDefinitionParser("property-override", new PropertyOverrideBeanDefinitionParser());
        registerBeanDefinitionParser("annotation-config", new AnnotationConfigBeanDefinitionParser());
        // 我们关注的内容
        registerBeanDefinitionParser("component-scan", new ComponentScanBeanDefinitionParser());
        registerBeanDefinitionParser("load-time-weaver", new LoadTimeWeaverBeanDefinitionParser());
        registerBeanDefinitionParser("spring-configured", new SpringConfiguredBeanDefinitionParser());
        registerBeanDefinitionParser("mbean-export", new MBeanExportBeanDefinitionParser());
        registerBeanDefinitionParser("mbean-server", new MBeanServerBeanDefinitionParser());
    }

}

你可以再回去看看parseCustomElement方法的最后一行了
它最终会经过 ComponentScanBeanDefinitionParser 这个解析器去解析配置,将所扫到的内容注册成BeanDefinition

我这的目的只是简单的说明 component-sace 开始工作的地方, 并不牵扯具体实现, 感兴趣自己追踪下
@ComponentScan注解与xml工作方式相同

补充: aop-config的配置也走一样的方式 毕竟都是'自定义标签'

我们这里做了什么？
    1. 根据namespaceUri从spring.handler中找到目标类并实例化
    2. 根据目标类实例化展开一些列的解析 (这个部分这里不讲, 和本文内容有点不想管)
```
**补充: 这里补充一下关于aop的xml解析, 因为我写到后面发现这里还是要说一下的。**
```
// NamespaceHandlerSupport

// 与@ComponentScan一样也是通过`parseBeanDefinitionElement(ele)`进入的,并且使用的是`spring.handlers`文件中的`AopNamespaceHandler`进行的处理
// 我们直接进入到 NamespaceHandlerSupport类看代码

@Override
@Nullable
public BeanDefinition parse(Element element, ParserContext parserContext) {
    // 这里通过下面的方法 获取到 ConfigBeanDefinitionParser 实例
    BeanDefinitionParser parser = findParserForElement(element, parserContext);
    // 调用其中的 parse 方法
    return (parser != null ? parser.parse(element, parserContext) : null);
}

@Nullable
private BeanDefinitionParser findParserForElement(Element element, ParserContext parserContext) {
    // 获取到标签的名称 例如: aop:config 会获取到 config
    String localName = parserContext.getDelegate().getLocalName(element);
    // this.parsers 这个东西是在 DefaultNamespaceHandlerResolver 类中通过调用 namespaceHandler.init(); 初始化的
    // 这里就会获取到 ConfigBeanDefinitionParser 这个类实例
    BeanDefinitionParser parser = this.parsers.get(localName);
    if (parser == null) {
        parserContext.getReaderContext().fatal(
                "Cannot locate BeanDefinitionParser for element [" + localName + "]", element);
    }
    return parser;
}

// 通过上面的 parser.parse(..) 调用, 跟进到 ConfigBeanDefinitionParser.class 中看一下
// ConfigBeanDefinitionParser
@Override
@Nullable
public BeanDefinition parse(Element element, ParserContext parserContext) {
    // 一个组合的ComponentDefinition, 这个不用关心
    CompositeComponentDefinition compositeDef =
            new CompositeComponentDefinition(element.getTagName(), parserContext.extractSource(element));
    parserContext.pushContainingComponent(compositeDef);

    // 这一行我们看一下, 见下面的代码 可以看出来是注册了一个 BeanDefinition
    configureAutoProxyCreator(parserContext, element);

    // 处理 aop:config 的子节点(例如: pointcut  advisor  aspect)
    
    // 该方法剩余代码就不跟了
    List<Element> childElts = DomUtils.getChildElements(element);
    for (Element elt: childElts) {
        String localName = parserContext.getDelegate().getLocalName(elt);
        if (POINTCUT.equals(localName)) { // 处理pointcut
            // 具体工作是: 解析切点(解析成BeanDefinition), 注册到BeanFactory
            // 一些做法的简单说明: 将AspectJExpressionPointcut.class(作为beanClass)和一些标签上的元素(表达式等内容)一起处理成一个BeanDefinition, 然后注册到BeanFactory中
            parsePointcut(elt, parserContext);
        }
        else if (ADVISOR.equals(localName)) { // 处理advisor
            // Advisor由切入点和Advice组成。
            parseAdvisor(elt, parserContext);
        }
        else if (ASPECT.equals(localName)) { // 处理aspect
            // 这个的调用比较繁琐
            // 1.先将从xml中读取的类似 aop:before(每个这都是一个advice) 标签解析成BeanDefinition(以具体的AdviceClass作为beanClass) (不注册)
            //        其中包含有一些关注点  参考:<aop:before method="before" pointcut-ref="logPointcut"/>进行解读
            //        1.1 ':before'将被识别为一个具体的AdviceClass(有AspectJMethodBeforeAdvice、AspectJAfterAdvice、AspectJAroundAdvice...), 而'method'的值将传递到相应类的构造函数中
            //        1.2 'pointcut-ref'的值将被解析成一个 BeanReference, 也会传入到AdviceClass的构造函数中
            // 2.创建以AspectJPointcutAdvisor.class作为beanClass的BeanDefinition, 将第一步得到的内容作为AspectJPointcutAdvisor.class的构造方法的参数。 (注册到bean工厂)
            //        说明: 这里是处理成了Advisor, 第一步是处理成了Advice 可以通过BeanDefinition的具体beanClass进行关注了解。
            //        (每次执行完第二步, 会使用一个集合存储起来Advisor, 第一步中的BeanReference也会被存储)
            // 3.将前两步所有涉及到的Advisor和BeanReference收集起来组成一个AspectComponentDefinition
            //        说明 AspectComponentDefinition不是BeanDefinition, 而是ComponentDefinition。 ComponentDefinition这东西是放在ParserContext类中等待处理的。
            //        这个东西其实就等同于xml中<aop:aspect ref="xxx">...</aop:aspect>的所有内容
            parseAspect(elt, parserContext);
        }
    }

    parserContext.popAndRegisterContainingComponent();
    return null;
}

// configureAutoProxyCreator(parserContext, element) 经过多次跳转,最终有效的地方如下: 
// AopConfigUtils
@Nullable
public static BeanDefinition registerAspectJAutoProxyCreatorIfNecessary(
        BeanDefinitionRegistry registry, @Nullable Object source) {

    // 注意这里写死的 AspectJAwareAdvisorAutoProxyCreator.class 看类名也知道是用来给被切入的类创建动态代理的
    return registerOrEscalateApcAsRequired(AspectJAwareAdvisorAutoProxyCreator.class, registry, source);
}

// AopConfigUtils
@Nullable
private static BeanDefinition registerOrEscalateApcAsRequired(
        Class<?> cls, BeanDefinitionRegistry registry, @Nullable Object source) {

    Assert.notNull(registry, "BeanDefinitionRegistry must not be null");

    if (registry.containsBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME)) {
        BeanDefinition apcDefinition = registry.getBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME);
        if (!cls.getName().equals(apcDefinition.getBeanClassName())) {
            int currentPriority = findPriorityForClass(apcDefinition.getBeanClassName());
            int requiredPriority = findPriorityForClass(cls);
            if (currentPriority < requiredPriority) {
                apcDefinition.setBeanClassName(cls.getName());
            }
        }
        return null;
    }

    // 我们先看这里, 尽量减少关注的内容
    // 这里将传进来的 AspectJAwareAdvisorAutoProxyCreator.class 搞成一个 BeanDefinition
    RootBeanDefinition beanDefinition = new RootBeanDefinition(cls);
    beanDefinition.setSource(source);
    beanDefinition.getPropertyValues().add("order", Ordered.HIGHEST_PRECEDENCE);
    beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
    // 然后在这里 以 "org.springframework.aop.config.internalAutoProxyCreator" 的名称 注册到 BeanFactory 中
    registry.registerBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME, beanDefinition);
    return beanDefinition;
}


以上案例分析是以如下xml代码进行的
<aop:config>
    <aop:pointcut id="logPointcut" expression="execution(* com.example.demo..*(..))"/>
    <aop:aspect ref="logAspect">                                   <!--aspect-->
        <aop:before method="before" pointcut-ref="logPointcut"/>   <!--advice--> --> <!--advisor-->
        <aop:after method="after" pointcut-ref="logPointcut"/>     <!--advice--> --> <!--advisor-->
    </aop:aspect>
</aop:config>

我们这里做了什么(只关注核心内容)？
    1. 首先将AspectJAwareAdvisorAutoProxyCreator.class以org.springframework.aop.config.internalAutoProxyCreator作为beanName注册到BeanFactory中
    2. 将每一个pointcut解析并注册到BeanFactory中
    3. 将每一个advice处理成advisor并注册到BeanFactory中
    4. 将每一个aop:aspect形成一个AspectComponentDefinition注册到ParserContext中
```

至此`ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory()`分析才算真的结束了。  
它做了什么?(主要内容)
- 创建`BeanFactory`  (这里默认是`DefaultListableBeanFactory.class`)
- 解析xml并构建`BeanDefinition`最后注册到`BeanFactory`中 (有提到 property标签的加载、@ComponentScan的运行时机、切面的操作)

这里附上一些额外的图方便理解上文.    
**AbstractAspectJAdvice类的简单结构关系图:**   
![AbstractAspectJAdvice类的简单结构关系图](images/AbstractAspectJAdvice类的简单结构关系图.png)
**AopNamespaceHandler类内容:** 
![AopNamespaceHandler类内容](images/AopNamespaceHandler类内容.png)

回到最上面的`refresh()`方法,再看下一行`prepareBeanFactory(beanFactory);`
```
protected void prepareBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    // Tell the internal bean factory to use the context's class loader etc.
    beanFactory.setBeanClassLoader(getClassLoader());
    beanFactory.setBeanExpressionResolver(new StandardBeanExpressionResolver(beanFactory.getBeanClassLoader()));
    beanFactory.addPropertyEditorRegistrar(new ResourceEditorRegistrar(this, getEnvironment()));

    // Configure the bean factory with context callbacks.
    beanFactory.addBeanPostProcessor(new ApplicationContextAwareProcessor(this));
    // Ignore the given dependency interface for autowiring.
    beanFactory.ignoreDependencyInterface(EnvironmentAware.class);
    beanFactory.ignoreDependencyInterface(EmbeddedValueResolverAware.class);
    beanFactory.ignoreDependencyInterface(ResourceLoaderAware.class);
    beanFactory.ignoreDependencyInterface(ApplicationEventPublisherAware.class);
    beanFactory.ignoreDependencyInterface(MessageSourceAware.class);
    beanFactory.ignoreDependencyInterface(ApplicationContextAware.class);

    // BeanFactory interface not registered as resolvable type in a plain factory.
    // MessageSource registered (and found for autowiring) as a bean.
    beanFactory.registerResolvableDependency(BeanFactory.class, beanFactory);
    beanFactory.registerResolvableDependency(ResourceLoader.class, this);
    beanFactory.registerResolvableDependency(ApplicationEventPublisher.class, this);
    beanFactory.registerResolvableDependency(ApplicationContext.class, this);

    // Register early post-processor for detecting inner beans as ApplicationListeners.
    beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(this));

    // Detect a LoadTimeWeaver and prepare for weaving, if found.
    if (beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
        beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
        // Set a temporary ClassLoader for type matching.
        beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
    }

    // 提前注册一些环境的单例对象
    // Register default environment beans.
    if (!beanFactory.containsLocalBean(ENVIRONMENT_BEAN_NAME)) {
        beanFactory.registerSingleton(ENVIRONMENT_BEAN_NAME, getEnvironment());
    }
    if (!beanFactory.containsLocalBean(SYSTEM_PROPERTIES_BEAN_NAME)) {
        beanFactory.registerSingleton(SYSTEM_PROPERTIES_BEAN_NAME, getEnvironment().getSystemProperties());
    }
    if (!beanFactory.containsLocalBean(SYSTEM_ENVIRONMENT_BEAN_NAME)) {
        beanFactory.registerSingleton(SYSTEM_ENVIRONMENT_BEAN_NAME, getEnvironment().getSystemEnvironment());
    }
}
```
它里面没有什么需要过度关注的,起码对我来说... 无非就是向`BeanFactory`中注册了一些东西(单例也好、BeanPostProcessor也罢)

这里引入2个新的概念`Aware`和`BeanPostProcessor`  
**1.关于`Aware`简单描述**  
在通常情况下所有`Bean`无法获知到它处在于一个容器中,这理论上是一种很棒的设计(耦合性极低),也能随意更换容器。  
但是感知不到上层就用不到上层的功能资源,这对一些功能会有限制。`Aware`就是一个用来让`Bean`感知到上层容器,使其能使用其中功能资源的接口。  

`Aware`常见类结构关系图如下(就是随便列了几个...)  (具体使用方法就百度吧..)   
![Aware类结构关系图](images/Aware类结构关系图.png)  

**2.关于`BeanPostProcessor`的简单描述**    
单说该接口内的方法..都是在最下面`initializeBean`方法中调用的。(实际在 Bean 创建完成后   InitializeBean 接口方法调用前后)  


然后目光转向`postProcessBeanFactory(beanFactory)`,这是对`BeanFactory`的 PostProcess, 可以自己定义实现。  
由于`ClassPathXmlApplicationContext`和`AnnotationConfigApplicationContext`都没有其具体实现,所以我并不关注。  
如果你有兴趣可以看看这篇博客,[点击进入](https://blog.csdn.net/cgj296645438/article/details/80119319)(随便找的)。  

**`BeanFactory`的后处理简单描述:**  
它的工作时机是在所有的`BeanDefinition`加载完成之后,`Bean`实例化之前。  
在此之间,你可以通过自己的代码实现,对`BeanFactory`本身和其内部数据进行一些你想要的操作。  


再往下看`invokeBeanFactoryPostProcessors(beanFactory)`,这个看名称大致知道作用, 有时间以后补上。


再往下`registerBeanPostProcessors(beanFactory);`。  
这个就值得关注了,该方法是针对`Bean`的后处理,和上面`BeanFactoryPostProcessors`不一样。

还记得上面对`aop:config`标签的解析过程说明吗?   
其中'我们做了什么'中第1点中提到的`AspectJAwareAdvisorAutoProxyCreator`就是一个`BeanPostProcessor`。准确点应该说,它的爷爷辈实现了该接口  

补充一点:   
在使用`@ComponentScan`时,`ComponentScanBeanDefinitionParser`中的`registerComponents(XmlReaderContext, Set<BeanDefinitionHolder>, Element)`方法会注册两个额外的`BeanPostProcessor`。  
一个是`org.springframework.context.annotation.internalAutowiredAnnotationProcessor`一个是`org.springframework.context.annotation.internalCommonAnnotationProcessor`。  

下面详细看一下`registerBeanPostProcessors`方法  
```
public static void registerBeanPostProcessors(
        ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {
    // 如果你使用我的demo项目, 调试的时候会看到这里有3个值
    // 1.org.springframework.context.annotation.internalAutowiredAnnotationProcessor
    // 2.org.springframework.context.annotation.internalCommonAnnotationProcessor
    // 3.org.springframework.aop.config.internalAutoProxyCreator   -> 这个就是AspectJAwareAdvisorAutoProxyCreator类作为bean的id了
    // 说明: prepareBeanFactory(beanFactory);中添加的BeanPostProcessor并不是以Bean的形式存在的,所以这里没有它们
    // 在AbstractBeanFactory类中有个List<BeanPostProcessor> beanPostProcessors属性专门存放BeanPostProcessor, 而上面那些搞到BeanDefinition里面的 到下面的代码中会全部添加到这里面
    String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);

    // Register BeanPostProcessorChecker that logs an info message when
    // a bean is created during BeanPostProcessor instantiation, i.e. when
    // a bean is not eligible for getting processed by all BeanPostProcessors.
    int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length;
    
    // 这里有新加了一个BeanPostProcessor
    // 再次补充: addBeanPostProcessor方法中还有对InstantiationAwareBeanPostProcessor和DestructionAwareBeanPostProcessor类型的检查,本文可能并不会说明,但有些源码中还是能看到的 
    beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));

    // 注意一下下面这句注释
    // Separate between BeanPostProcessors that implement PriorityOrdered,
    // Ordered, and the rest. (翻译: 将实现了PriorityOrdered、Ordered和剩余的BeanPostProcessors区分开来) 这3者不做说明,要不篇幅太长
    List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
    List<BeanPostProcessor> internalPostProcessors = new ArrayList<>();
    List<String> orderedPostProcessorNames = new ArrayList<>();
    List<String> nonOrderedPostProcessorNames = new ArrayList<>();
    for (String ppName : postProcessorNames) {
        // 实现了PriorityOrdered
        if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) { 
            // getBean(...) 将BeanPostProcessor实例化, 变成我们通常意义上使用的bean  getBean方法后面才会分析,这里知道是干啥的就行
            BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
            priorityOrderedPostProcessors.add(pp);
            if (pp instanceof MergedBeanDefinitionPostProcessor) {
                internalPostProcessors.add(pp);
            }
        }
        else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
            // 实现了Ordered先添加到一个集合中
            orderedPostProcessorNames.add(ppName);
        }
        else {
            // 剩余的添加到另一个集合中
            nonOrderedPostProcessorNames.add(ppName);
        }
    }

    // ------------------------- 处理实现了PriorityOrdered的BeanPostProcessors -------------------------------
    // First, register the BeanPostProcessors that implement PriorityOrdered.
    sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
    // 将priorityOrderedPostProcessors集合添加到BeanFactory的beanPostProcessors集合中(就是上面说的那个集合)
    registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);

    // --------------------------- 处理实现了Ordered的BeanPostProcessors -------------------------------
    // Next, register the BeanPostProcessors that implement Ordered.
    List<BeanPostProcessor> orderedPostProcessors = new ArrayList<>();
    for (String ppName : orderedPostProcessorNames) {
        BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
        orderedPostProcessors.add(pp);
        if (pp instanceof MergedBeanDefinitionPostProcessor) {
            internalPostProcessors.add(pp);
        }
    }
    sortPostProcessors(orderedPostProcessors, beanFactory);
    registerBeanPostProcessors(beanFactory, orderedPostProcessors);

    // --------------------------- 处理剩下的的BeanPostProcessors -------------------------------
    // Now, register all regular BeanPostProcessors.
    List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<>();
    for (String ppName : nonOrderedPostProcessorNames) {
        BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
        nonOrderedPostProcessors.add(pp);
        if (pp instanceof MergedBeanDefinitionPostProcessor) {
            internalPostProcessors.add(pp);
        }
    }
    registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);

    // 看下面这句注释(重新注册并不会重复  至于这步操作的原因你看完上面也应该知道了)
    // Finally, re-register all internal BeanPostProcessors.
    sortPostProcessors(internalPostProcessors, beanFactory); // 至于按照什么排序的 你可以进到这个方法看一样
    registerBeanPostProcessors(beanFactory, internalPostProcessors);

    // Re-register post-processor for detecting inner beans as ApplicationListeners,
    // moving it to the end of the processor chain (for picking up proxies etc).
    // 这个就不说了
    beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));

    // 到这里`beanPostProcessors`中应该有6~9个值了(我没去看,凭记忆猜的)
    // 有从 obtainFreshBeanFactory()、prepareBeanFactory(beanFactory) 中添加的 也有 从该方法中添加的。
}
```
这一步干了什么？  
- 将实现了`BeanPostProcesser`的接口的从`BeanDefinition`注册表中找到并**实例化**,顺便(按排序)添加到`BeanFactory`的`beanPostProcessors`集合中


再往下一步,`initMessageSource()`
```
// AbstractApplicationContext
protected void initMessageSource() {
    ConfigurableListableBeanFactory beanFactory = getBeanFactory();
    if (beanFactory.containsLocalBean(MESSAGE_SOURCE_BEAN_NAME)) {
        this.messageSource = beanFactory.getBean(MESSAGE_SOURCE_BEAN_NAME, MessageSource.class);
        // Make MessageSource aware of parent MessageSource.
        if (this.parent != null && this.messageSource instanceof HierarchicalMessageSource) {
            HierarchicalMessageSource hms = (HierarchicalMessageSource) this.messageSource;
            if (hms.getParentMessageSource() == null) {
                // Only set parent context as parent MessageSource if no parent MessageSource
                // registered already.
                // 通过Java获取到引用的机制  再设置一些属性到对象中
                hms.setParentMessageSource(getInternalParentMessageSource());
            }
        }
        if (logger.isTraceEnabled()) {
            logger.trace("Using MessageSource [" + this.messageSource + "]");
        }
    }
    else {
        // Use empty MessageSource to be able to accept getMessage calls.
        DelegatingMessageSource dms = new DelegatingMessageSource();
        dms.setParentMessageSource(getInternalParentMessageSource());
        // 将引用传递给本类的对象
        this.messageSource = dms;
        // 如果不包含,就搞一个单例(在这里实例化,直接丢到单例注册表中)
        // 说明: 我说的 BeanDefinition注册表 和 单例注册表并不是一个东西, 单例注册表在DefaultSingletonBeanRegistry类中
        beanFactory.registerSingleton(MESSAGE_SOURCE_BEAN_NAME, this.messageSource);
        if (logger.isTraceEnabled()) {
            logger.trace("No '" + MESSAGE_SOURCE_BEAN_NAME + "' bean, using [" + this.messageSource + "]");
        }
    }
}
```
里面仅仅是将`DelegatingMessageSource`实例化后赋值给`this.messageSource`,然后注册成单例。  

再下一步`initApplicationEventMulticaster();`,这个和上一步一样。  
实例化`SimpleApplicationEventMulticaster.class`赋给`this.applicationEventMulticaster`然后注册成单例。  
这两步的2个类有什么作用就靠自己去发掘了,这里不写了。  

再下一步的`onRefresh();`是个空实现且没有子类实现,直接跳过。

再下一步`registerListeners()`,注册监听者。本文中的代码并没有这玩意,里面其实一个都不走。我大致知道含义,但是不确定对不对,自己研究吧。

继续`finishBeanFactoryInitialization(beanFactory);`这个是个重点  
原注释'Instantiate all remaining (non-lazy-init) singletons.'  
```
// AbstractApplicationContext
protected void finishBeanFactoryInitialization(ConfigurableListableBeanFactory beanFactory) {
    // Initialize conversion service for this context. （初始化上下文的'ConversionService'）
    // ConversionService可以自己了解下, 懒得写了
    // 如果真想了解 这是一个关于介绍使用的博客 http://www.cnblogs.com/tengfeixinxing/p/7012958.html
    // 这是一个关于说明的博客 https://www.cnblogs.com/abcwt112/p/7447435.html
    // 主要我这是想简单的总览ioc aop... 所以有些东西该跳就跳了
    if (beanFactory.containsBean(CONVERSION_SERVICE_BEAN_NAME) &&
            beanFactory.isTypeMatch(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class)) {
        beanFactory.setConversionService(
                beanFactory.getBean(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class));
    }

    // Register a default embedded value resolver if no bean post-processor
    // (such as a PropertyPlaceholderConfigurer bean) registered any before:
    // at this point, primarily for resolution in annotation attribute values.
    if (!beanFactory.hasEmbeddedValueResolver()) {
        beanFactory.addEmbeddedValueResolver(strVal -> getEnvironment().resolvePlaceholders(strVal));
    }

    // Initialize LoadTimeWeaverAware beans early to allow for registering their transformers early.
    // (尽早初始化LoadTimeWeaverAware bean以允许尽早注册其变换器。)
    // 这里它也是个Aware
    // 这里列举出我从网上找的几个Aware接口具体是干什么的一个对应关系
    // LoadTimeWeaverAware    加载Spring Bean时织入第三方模块，如AspectJ
    // BeanClassLoaderAware    加载Spring Bean的类加载器
    // ResourceLoaderAware    底层访问资源的加载器
    // BeanFactoryAware    得到BeanFactory引用
    // ServletConfigAware    得到ServletConfig
    // ServletContextAware    得到ServletContext
    // MessageSourceAware    国际化
    // ApplicationEventPublisherAware    应用事件
    String[] weaverAwareNames = beanFactory.getBeanNamesForType(LoadTimeWeaverAware.class, false, false);
    for (String weaverAwareName : weaverAwareNames) {
        getBean(weaverAwareName);
    }

    // Stop using the temporary ClassLoader for type matching. (停止使用临时ClassLoader进行类型匹配。)
    beanFactory.setTempClassLoader(null);

    // Allow for caching all bean definition metadata, not expecting further changes.
    // 冻结配置, 在实例化bean之前 禁止在配置了。 我记得java虚拟机方面的书里面有和个关于gc的比喻用在这很形象: 你妈妈不希望她一边扫地,你一边扔垃圾...
    beanFactory.freezeConfiguration();

    // Instantiate all remaining (non-lazy-init) singletons.
    // 这里是重点,下面贴上方法内部代码
    beanFactory.preInstantiateSingletons();
}

// DefaultListableBeanFactory
@Override
public void preInstantiateSingletons() throws BeansException {
    if (logger.isTraceEnabled()) {
        logger.trace("Pre-instantiating singletons in " + this);
    }

    // Iterate over a copy to allow for init methods which in turn register new bean definitions.
    // While this may not be part of the regular factory bootstrap, it does otherwise work fine.
    // 说明: this.beanDefinitionNames是一个记录了所有BeanDefinitionName的列表
    List<String> beanNames = new ArrayList<>(this.beanDefinitionNames);

    // Trigger initialization of all non-lazy singleton beans...
    for (String beanName : beanNames) {
        RootBeanDefinition bd = getMergedLocalBeanDefinition(beanName);
        if (!bd.isAbstract() && bd.isSingleton() && !bd.isLazyInit()) {
            // 如果 BeanDefinition 不是 抽象的 且 是单例的 且 不是lazy-init
            if (isFactoryBean(beanName)) {
                Object bean = getBean(FACTORY_BEAN_PREFIX + beanName); // FactoryBean的前缀在原有定义上会加上一个'&'符号
                if (bean instanceof FactoryBean) {
                    // 如果是 FactoryBean  什么是FactoryBean百度下吧.写的恶心。
                    // Bean 和 FactoryBean 可以都理解为 Bean。  普通Bean是通过反射机制注入的值。 
                    // 当一个类中属性超多的时候, 反射或许会成为累赘, 这时候你可以使用 FactoryBean, 它可以让你自己决定怎么初始化
                    final FactoryBean<?> factory = (FactoryBean<?>) bean;
                    boolean isEagerInit;
                    if (System.getSecurityManager() != null && factory instanceof SmartFactoryBean) {
                        isEagerInit = AccessController.doPrivileged((PrivilegedAction<Boolean>)
                                        ((SmartFactoryBean<?>) factory)::isEagerInit,
                                getAccessControlContext());
                    }
                    else {
                        isEagerInit = (factory instanceof SmartFactoryBean &&
                                ((SmartFactoryBean<?>) factory).isEagerInit());
                    }
                    if (isEagerInit) {
                        getBean(beanName);
                    }
                }
            }
            else {
                // 如果是普通 Bean
                getBean(beanName);
            }
        }
    }
    
    // 到这里满足!bd.isAbstract() && bd.isSingleton() && !bd.isLazyInit()的就实例化完了

    // Trigger post-initialization callback for all applicable beans...
    for (String beanName : beanNames) {
        // 获取指定类的单例对象, 没有就返回null
        Object singletonInstance = getSingleton(beanName);

        // SmartInitializingSingleton是个接口,且只有一个'void afterSingletonsInstantiated();'方法, 看名称应该能知道是干什么的吧。
        // 这里就是集中调用该方法的地方
        if (singletonInstance instanceof SmartInitializingSingleton) {
            final SmartInitializingSingleton smartSingleton = (SmartInitializingSingleton) singletonInstance;
            if (System.getSecurityManager() != null) {
                AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                    smartSingleton.afterSingletonsInstantiated();
                    return null;
                }, getAccessControlContext());
            }
            else {
                smartSingleton.afterSingletonsInstantiated();
            }
        }
    }
}
```
我们分析过了`preInstantiateSingletons()`方法发现其中最重要的还是一个`getBean(..)`  
下面分析下内部的实现  
```
// AbstractBeanFactory

@Override
public Object getBean(String name) throws BeansException {
    return doGetBean(name, null, null, false);
}

// name  - 要检索的bean的名称
// requiredType  - 要检索的bean的必需类型
// args  - 使用显式参数创建bean实例时使用的参数（仅在创建新实例时应用，而不是在检索现有实例时应用）
// typeCheckOnly  - 是否为类型检查获取实例，而不是实际使用
protected <T> T doGetBean(final String name, @Nullable final Class<T> requiredType,
            @Nullable final Object[] args, boolean typeCheckOnly) throws BeansException {

    // 提示: 你在阅读该部分代码的时候会发现DefaultSingletonBeanRegistry.class中有singletonFactories、earlySingletonObjects、singletonObjects, 你可能一下理解不了它们是干什么的。
    // 这里说明: 这是Spring为了解决循环依赖问题使用的三级缓存
    // 一级: singletonObjects：单例对象的cache
    // 二级: earlySingletonObjects：提前暴光的单例对象的Cache 
    // 三级: singletonFactories：单例对象工厂的cache 
    // 
    // 在一个bean创建中,首先尝试从singletonObjects中获取。如果没有且单例正在创建中,就从earlySingletonObjects中取。
    // 如果还没有且allowEarlyReference就通过singletonFactories获取一个具体的ObjectFactory然后调用getObject获取。
    // 
    // 注意: 这是解决字段的循环以来的方式, 构造器的循环以来仍然会直接抛错

    final String beanName = transformedBeanName(name);
    Object bean; // 这个是最终的结果
    
    // Eagerly check singleton cache for manually registered singletons.
    // 在这个方法调用中你会看到上面我描述的寻找过程
    Object sharedInstance = getSingleton(beanName);

    if (sharedInstance != null && args == null) {
        if (logger.isTraceEnabled()) {
            if (isSingletonCurrentlyInCreation(beanName)) {
                logger.trace("Returning eagerly cached instance of singleton bean '" + beanName +
                        "' that is not fully initialized yet - a consequence of a circular reference");
            }
            else {
                logger.trace("Returning cached instance of singleton bean '" + beanName + "'");
            }
        }
        bean = getObjectForBeanInstance(sharedInstance, name, beanName, null);
    }

    else {
        // Fail if we're already creating this bean instance:
        // We're assumably within a circular reference.
        if (isPrototypeCurrentlyInCreation(beanName)) {
            throw new BeanCurrentlyInCreationException(beanName);
        }

        // Check if bean definition exists in this factory.
        BeanFactory parentBeanFactory = getParentBeanFactory();
        if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
            // 如果有parentBeanFactory且当前类没有该BeanDefinition, 则去parentBeanFactory里寻找, 里面通过if判断确定不同的情况使用不同的方式而已

            // Not found -> check parent.
            // ... 
        }

        if (!typeCheckOnly) {
            // 将指定的bean标记为已创建(或即将创建)。 这允许beanFactory优化其缓存以重复创建指定的bean。
            // 具体做法就是看alreadyCreated中有没有这个beanName, 如果没有就加上
            // 注意: 这里没开始为BeanDefinition统一实例化
            markBeanAsCreated(beanName);
        }

        try {
            // 先看一下这里, 大致过程是: 经过一系列对 从子类中获取的 BeanDefinition 的操作(主要是克隆), 使其变为一个RootBeanDefinition, 然后添加到AbstractBeanFactory的mergedBeanDefinitions状态中。

            // 谈一下bean的父子关系,在xml中可以通过parent设置。 大致意思是子bean会继承父bean的参数使用,但子bean可以自己重写。
            // 例如: 
            // <bean id="parent" class="com.x.x.Parent">
            //     <property name="name" value="foo"/>
            // </bean>
            // <bean id="child" class="com.x.x.Child" parent="parent">
            //     <property name="age" value="18"/>
            // </bean>
            // Child 就拥有了 name 属性的值, 但是在Java代码层面2个类并没有关系
            // 一般这情况都是用来当模板,减少配置的。 Spring提供了继承自AbstractBeanDefinition的ChildBeanDefinition来表示子bean。
            // 方法里面自己看吧
            final RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
            // 检查一下 mbd 的 isAbstract。 如果true, 抛出异常BeanIsAbstractException
            checkMergedBeanDefinition(mbd, beanName, args);

            // 看下面这个注释, 确保当前bean依赖的bean的初始化。
            // Guarantee initialization of beans that the current bean depends on.
            // 这里获取的是 bean 标签中的 depends-on 的值
            String[] dependsOn = mbd.getDependsOn();
            if (dependsOn != null) {
                // 例子: A dependsOn B
                // 如果现在在创建A,到这一步了,先看看A有没有依赖B
                // 如果没有就将B作为key A作为value存储
                // 然后对B调用getBean,重复上述内容, 如果key为B时查到有A 就是循环依赖 
                for (String dep : dependsOn) {
                    // 检查是否循环依赖, 具体做法是通过维护Map<String, Set<String>>这样的一个数据结构, 细节自己看
                    if (isDependent(beanName, dep)) {
                        throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                                "Circular depends-on relationship between '" + beanName + "' and '" + dep + "'");
                    }
                    // 如果没有循环依赖，则被依赖项作为key, 依赖项作为value存入上述的数据结构中, 细节自己看
                    registerDependentBean(dep, beanName);
                    try {
                        // 将依赖项先行实例化
                        getBean(dep);
                    }
                    catch (NoSuchBeanDefinitionException ex) {
                        throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                                "'" + beanName + "' depends on missing bean '" + dep + "'", ex);
                    }
                }
            }
            // 这告诉我们 depends-on 不要陷入循环的坑中

            // Create bean instance. 
            // 我们这里只说单例
            if (mbd.isSingleton()) {
                // 这里是重点  getSingleton 和 createBean
                sharedInstance = getSingleton(beanName, () -> {
                    try {
                        return createBean(beanName, mbd, args);
                    }
                    catch (BeansException ex) {
                        // Explicitly remove instance from singleton cache: It might have been put there
                        // eagerly by the creation process, to allow for circular reference resolution.
                        // Also remove any beans that received a temporary reference to the bean.
                        destroySingleton(beanName);
                        throw ex;
                    }
                });
                // 如果是普通Bean的实例话, 直接返回指定实例。如果是 FactoryBean 的话, 就要经过一番处理了, 这里不提
                // 至于FactoryBean是啥.参考 https://www.cnblogs.com/aspirant/p/9082858.html 自己理解,这里就不提了
                bean = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
            }
            else if (mbd.isPrototype()) {
                // 这里是多例(原型),就创建一个新实例
                // It's a prototype -> create a new instance.
                Object prototypeInstance = null;
                try {
                    beforePrototypeCreation(beanName);
                    prototypeInstance = createBean(beanName, mbd, args);
                }
                finally {
                    afterPrototypeCreation(beanName);
                }
                bean = getObjectForBeanInstance(prototypeInstance, name, beanName, mbd);
            }
            else {
                // 其他的scope的实例化地点。
                String scopeName = mbd.getScope();
                final Scope scope = this.scopes.get(scopeName);
                if (scope == null) {
                    throw new IllegalStateException("No Scope registered for scope name '" + scopeName + "'");
                }
                try {
                    Object scopedInstance = scope.get(beanName, () -> {
                        beforePrototypeCreation(beanName);
                        try {
                            return createBean(beanName, mbd, args);
                        }
                        finally {
                            afterPrototypeCreation(beanName);
                        }
                    });
                    bean = getObjectForBeanInstance(scopedInstance, name, beanName, mbd);
                }
                catch (IllegalStateException ex) {
                    throw new BeanCreationException(beanName,
                            "Scope '" + scopeName + "' is not active for the current thread; consider " +
                            "defining a scoped proxy for this bean if you intend to refer to it from a singleton",
                            ex);
                }
            }
        }
        catch (BeansException ex) {
            cleanupAfterBeanCreationFailure(beanName);
            throw ex;
        }
    }

    // Check if required type matches the type of the actual bean instance.
    if (requiredType != null && !requiredType.isInstance(bean)) {
        try {
            T convertedBean = getTypeConverter().convertIfNecessary(bean, requiredType);
            if (convertedBean == null) {
                throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
            }
            return convertedBean;
        }
        catch (TypeMismatchException ex) {
            if (logger.isTraceEnabled()) {
                logger.trace("Failed to convert bean '" + name + "' to required type '" +
                        ClassUtils.getQualifiedName(requiredType) + "'", ex);
            }
            throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
        }
    }
    return (T) bean;
}

```
先来分析下`getSingleton(String, ObjectFactory<?>)`, 第二个参数是函数式编程的写法。
```
// DefaultSingletonBeanRegistry

public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
    // 参数是在定义lambda时就已经确认的
    
    Assert.notNull(beanName, "Bean name must not be null");
    // 对singletonObjects实例对象的操作得排队来
    synchronized (this.singletonObjects) {
        // 首先, 先尝试从singletonObjects中获取一下
        Object singletonObject = this.singletonObjects.get(beanName);
        if (singletonObject == null) {
            if (this.singletonsCurrentlyInDestruction) {
                // 如果单例正在销毁 (在AbstractBeanFactory中的函数式方法中可以看到catch块的销毁方法)
                throw new BeanCreationNotAllowedException(beanName,
                        "Singleton bean creation not allowed while singletons of this factory are in destruction " +
                        "(Do not request a bean from a BeanFactory in a destroy method implementation!)");
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Creating shared instance of singleton bean '" + beanName + "'");
            }
            // 在创建前做一个判断, 如果通过就顺便设置一下singletonsCurrentlyInCreation(添加进去)，下面贴了代码
            beforeSingletonCreation(beanName);
            boolean newSingleton = false;
            boolean recordSuppressedExceptions = (this.suppressedExceptions == null);
            if (recordSuppressedExceptions) {
                this.suppressedExceptions = new LinkedHashSet<>();
            }
            try {
                // 通过调用 传入的方法 来获取单例(其实就是调用createBean(...))
                singletonObject = singletonFactory.getObject();
                newSingleton = true;
            }
            catch (IllegalStateException ex) {
                // Has the singleton object implicitly appeared in the meantime ->
                // if yes, proceed with it since the exception indicates that state.
                // 单例对象是否在此期间隐式出现 -> 如果是，则继续执行
                singletonObject = this.singletonObjects.get(beanName);
                if (singletonObject == null) {
                    throw ex;
                }
            }
            catch (BeanCreationException ex) {
                if (recordSuppressedExceptions) {
                    for (Exception suppressedException : this.suppressedExceptions) {
                        ex.addRelatedCause(suppressedException);
                    }
                }
                throw ex;
            }
            finally {
                if (recordSuppressedExceptions) {
                    this.suppressedExceptions = null;
                }
                // 在创建后做一个判断, 如果通过就顺便清除一下singletonsCurrentlyInCreation
                afterSingletonCreation(beanName);
            }
            if (newSingleton) {
                // 这个调用DefaultSingletonBeanRegistry, 具体内容不多, 下面直接列出来
                addSingleton(beanName, singletonObject);
            }
        }
        return singletonObject;
    }
}

protected void beforeSingletonCreation(String beanName) {
    if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.add(beanName)) {
        throw new BeanCurrentlyInCreationException(beanName);
    }
}

protected void afterSingletonCreation(String beanName) {
    if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.remove(beanName)) {
        throw new IllegalStateException("Singleton '" + beanName + "' isn't currently in creation");
    }
}

// DefaultSingletonBeanRegistry
protected void addSingleton(String beanName, Object singletonObject) {
    synchronized (this.singletonObjects) {
        // 将获取到的单例对象添加到一级缓存中
        this.singletonObjects.put(beanName, singletonObject);
        // 移除三级缓存中的值
        this.singletonFactories.remove(beanName);
        // 移除二级缓存中的值
        this.earlySingletonObjects.remove(beanName);
        // 维护一个已注册的单例的名称集合(上面提到过), 上面三个都是Map
        this.registeredSingletons.add(beanName);
    }
}
```
`getSingleton(String, ObjectFactory<?>)`结束了, 我们看到它获取单例的方式:先从`singletonObjects`中获取, 如果没有就通过调用`singletonFactory.getObject()`进行创建。

接下来分析一下`createBean`方法
```
// AbstractAutowireCapableBeanFactory
protected Object createBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
            throws BeanCreationException {

    if (logger.isTraceEnabled()) {
        logger.trace("Creating instance of bean '" + beanName + "'");
    }
    RootBeanDefinition mbdToUse = mbd;

    // Make sure bean class is actually resolved at this point, and
    // clone the bean definition in case of a dynamically resolved Class
    // which cannot be stored in the shared merged bean definition.
    // 解析BeanClass,如果 mbd 中有beanClass直接拿过来, 如果没有就根据beanClassName解析,都没有就返回空
    Class<?> resolvedClass = resolveBeanClass(mbd, beanName);
    if (resolvedClass != null && !mbd.hasBeanClass() && mbd.getBeanClassName() != null) {
        // 如果是通过beanClassName解析出来的Class,克隆一份BeanDefinition, 且解析到的类不能存放在shared merged bean definition中
        mbdToUse = new RootBeanDefinition(mbd);
        mbdToUse.setBeanClass(resolvedClass);
    }

    // Prepare method overrides.
    try {
        // 和<lookup-method/>、<replaced-method/>有关,我还真没用过,以后注意到了再补充
        mbdToUse.prepareMethodOverrides();
    }
    catch (BeanDefinitionValidationException ex) {
        throw new BeanDefinitionStoreException(mbdToUse.getResourceDescription(),
                beanName, "Validation of method overrides failed", ex);
    }

    try {
        // Give BeanPostProcessors a chance to return a proxy instead of the target bean instance.
        // 事实上,这个方法的调用..算了,我新开个地址专门描述一下吧.. https://www.jianshu.com/p/8d42a8816cf5
        // 只要记得.这里在默认配置下特定返回 null 就ok了
        Object bean = resolveBeforeInstantiation(beanName, mbdToUse);
        if (bean != null) {
            return bean;
        }
    }
    catch (Throwable ex) {
        throw new BeanCreationException(mbdToUse.getResourceDescription(), beanName,
                "BeanPostProcessor before instantiation of bean failed", ex);
    }

    try {
        // 寻常的创建Bean
        Object beanInstance = doCreateBean(beanName, mbdToUse, args);
        if (logger.isTraceEnabled()) {
            logger.trace("Finished creating instance of bean '" + beanName + "'");
        }
        return beanInstance;
    }
    catch (BeanCreationException | ImplicitlyAppearedSingletonException ex) {
        // A previously detected exception with proper bean creation context already,
        // or illegal singleton state to be communicated up to DefaultSingletonBeanRegistry.
        throw ex;
    }
    catch (Throwable ex) {
        throw new BeanCreationException(
                mbdToUse.getResourceDescription(), beanName, "Unexpected exception during bean creation", ex);
    }
}
```
关于`InstantiationAwareBeanPostProcessor`这个东西,在本文不做阐述。自己搜下吧(其实看接口方法名称也能有个大概意思)。  

接着分析最后的`doCreateBean(beanName, mbdToUse, args)`方法 (真正的创建Bean的地方)  
```
// AbstractAutowireCapableBeanFactory
protected Object doCreateBean(final String beanName, final RootBeanDefinition mbd, final @Nullable Object[] args)
        throws BeanCreationException {

    // Instantiate the bean.
    BeanWrapper instanceWrapper = null;
    if (mbd.isSingleton()) {
        instanceWrapper = this.factoryBeanInstanceCache.remove(beanName);
    }
    if (instanceWrapper == null) {
        // 创建一个bean实例并生成包装对象(通常理解成是对beanClass的实例化就行了), 下面有分析 
        instanceWrapper = createBeanInstance(beanName, mbd, args);
    }
    // 实例化后的bean
    final Object bean = instanceWrapper.getWrappedInstance();
    Class<?> beanType = instanceWrapper.getWrappedClass();
    if (beanType != NullBean.class) {
        mbd.resolvedTargetType = beanType;
    }

    // Allow post-processors to modify the merged bean definition.
    synchronized (mbd.postProcessingLock) {
        if (!mbd.postProcessed) {
            try {
                applyMergedBeanDefinitionPostProcessors(mbd, beanType, beanName);
            }
            catch (Throwable ex) {
                throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                        "Post-processing of merged bean definition failed", ex);
            }
            mbd.postProcessed = true;
        }
    }

    // Eagerly cache singletons to be able to resolve circular references
    // even when triggered by lifecycle interfaces like BeanFactoryAware.
    // 如果当前操作的mbd是单例的 且 工厂允许循环引用
    boolean earlySingletonExposure = (mbd.isSingleton() && this.allowCircularReferences &&
            isSingletonCurrentlyInCreation(beanName));
    if (earlySingletonExposure) {
        // earlySingleton曝光
        if (logger.isTraceEnabled()) {
            logger.trace("Eagerly caching bean '" + beanName +
                    "' to allow for resolving potential circular references");
        }
        // 如果singletonObjects不包含当前beanName  // 如果一级缓存中没有值
        // 1.singletonFactories.put(beanName, singletonFactory);  // 单例对象工厂的cache  三级缓存添加
        // 2.earlySingletonObjects.remove(beanName);  // 提前暴光的单例对象的Cache  二级缓存中移除
        // 3.registeredSingletons.add(beanName);    // 标记当前bean的单例为以注册的
        // 这里的意思是在bean创建期间, 所有初始化完成的都会存储到singletonObjects中, 如果singletonObjects没有证明bean还没有加载完全
        // getEarlyBeanReference就如其名称一样获取bean的早期引用(依赖对象) 
        addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
    }

    // Initialize the bean instance.(初始化实例对象的状态)
    Object exposedObject = bean;
    try {
        // 填充属性, 稍后分析
        populateBean(beanName, mbd, instanceWrapper);
        // 这一步其实就是处理各种后续了(包括 设置bean的感知、调用bean的InitMethods、处理bean的其他注解、进行aop处理等)。 稍后分析
        exposedObject = initializeBean(beanName, exposedObject, mbd);
    }
    catch (Throwable ex) {
        if (ex instanceof BeanCreationException && beanName.equals(((BeanCreationException) ex).getBeanName())) {
            throw (BeanCreationException) ex;
        }
        else {
            throw new BeanCreationException(
                    mbd.getResourceDescription(), beanName, "Initialization of bean failed", ex);
        }
    }

    // 这下面就不说了
    if (earlySingletonExposure) {
        Object earlySingletonReference = getSingleton(beanName, false);
        if (earlySingletonReference != null) {
            if (exposedObject == bean) {
                exposedObject = earlySingletonReference;
            }
            else if (!this.allowRawInjectionDespiteWrapping && hasDependentBean(beanName)) {
                String[] dependentBeans = getDependentBeans(beanName);
                Set<String> actualDependentBeans = new LinkedHashSet<>(dependentBeans.length);
                for (String dependentBean : dependentBeans) {
                    if (!removeSingletonIfCreatedForTypeCheckOnly(dependentBean)) {
                        actualDependentBeans.add(dependentBean);
                    }
                }
                if (!actualDependentBeans.isEmpty()) {
                    throw new BeanCurrentlyInCreationException(beanName,
                            "Bean with name '" + beanName + "' has been injected into other beans [" +
                            StringUtils.collectionToCommaDelimitedString(actualDependentBeans) +
                            "] in its raw version as part of a circular reference, but has eventually been " +
                            "wrapped. This means that said other beans do not use the final version of the " +
                            "bean. This is often the result of over-eager type matching - consider using " +
                            "'getBeanNamesOfType' with the 'allowEagerInit' flag turned off, for example.");
                }
            }
        }
    }

    // Register bean as disposable.
    try {
        registerDisposableBeanIfNecessary(beanName, bean, mbd);
    }
    catch (BeanDefinitionValidationException ex) {
        throw new BeanCreationException(
                mbd.getResourceDescription(), beanName, "Invalid destruction signature", ex);
    }

    return exposedObject;
}
```
对`createBeanInstance`方法的分析,首先对名字分析可以得出方法的作用
```
// 这个方法就是用来将beanClass实例化的(不会对参数赋值的那种)
protected BeanWrapper createBeanInstance(String beanName, RootBeanDefinition mbd, @Nullable Object[] args) {
    // Make sure bean class is actually resolved at this point.
    // 确保此时实际解析了beanClass。 在上面有提到这个方法
    Class<?> beanClass = resolveBeanClass(mbd, beanName);

    if (beanClass != null && !Modifier.isPublic(beanClass.getModifiers()) && !mbd.isNonPublicAccessAllowed()) {
        // 类的访问权限的判断
        throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                "Bean class isn't public, and non-public access not allowed: " + beanClass.getName());
    }

    // 这玩意就是一个函数式接口  使用方式: Supplier<A> sup = A::new; sup.get();
    Supplier<?> instanceSupplier = mbd.getInstanceSupplier();
    if (instanceSupplier != null) {
        // 如果 RootBeanDefinition 中提供了用来实例化的方法 那就用它
        return obtainFromSupplier(instanceSupplier, beanName);
    }

    if (mbd.getFactoryMethodName() != null) {
        // 采用工厂方法实例化bean。 (这里主要的目的是通过调用其他类的方法来返回 实例对象)
        // 如果mbd参数指定类而不是factoryBean，或者工厂对象本身使用依赖注入配置的实例变量，则该方法可以是静态的。
        // 
        // public class ServiceFactory {
        //    // 非静态方法
        //    public XXService createService() { return new XXService(); }
        //    // 静态方法2
        //    public static XXService createStaticService2(String arg) { return new XXService(arg); }
        //    // 静态方法1
        //    public static XXService createStaticService() { return new XXService(); }
        // }
        // 静态的方式1:  (mbd参数指定类)
        //        <bean id="service1" class="a.b.c.ServiceFactory" factory-method="createStaticService"/>
        // 静态的方式2:  (工厂对象本身使用依赖注入配置的实例变量)
        //        <bean id="service3" class="a.b.c.ServiceFactory" factory-method="createStaticService2">
        //            <constructor-arg value="abc"/>  <!-- 如果要传依赖对象进入 value -> ref -->
        //        </bean>
        // 非静态的方式: 
        //         <bean id="serviceFactory" class="a.b.c.ServiceFactory" />
        //         <bean id="service2" factory-bean="serviceFactory" factory-method="createService"/>
        // 
        // 在经过该方法后会
        // 综上, 就不跟进去看了...XD 
        // 
        return instantiateUsingFactoryMethod(beanName, mbd, args);
    }

    // Shortcut when re-creating the same bean... (重新创建相同bean时的快捷方式...) 这一部分跳过, 暂时只分析单例, 其他的以后再说
    boolean resolved = false;
    boolean autowireNecessary = false;
    if (args == null) {
        synchronized (mbd.constructorArgumentLock) {
            // 如果已解析的构造函数或者FactoryMethod不为空, 对resolved和autowireNecessary进行标记
            if (mbd.resolvedConstructorOrFactoryMethod != null) {
                resolved = true;
                autowireNecessary = mbd.constructorArgumentsResolved;
            }
        }
    }
    if (resolved) {
        if (autowireNecessary) {
            // 这个以后再说 最终目的就是返回一个类的实例 (它太长了, 我这随便写的都2000行了..)
            return autowireConstructor(beanName, mbd, null, null);
        }
        else {
            return instantiateBean(beanName, mbd);
        }
    }

    // Candidate constructors for autowiring? (获取使用自动装配的类的候选构造函数)
    // 通过BeanPostProcessors确定构造函数, 通过AutowiredAnnotationBeanPostProcessor.class的determineCandidateConstructors方法获取构造函数列表
    // 看类名就能感觉到和@Autowired注解有关系
    Constructor<?>[] ctors = determineConstructorsFromBeanPostProcessors(beanClass, beanName);
    if (ctors != null || mbd.getResolvedAutowireMode() == AUTOWIRE_CONSTRUCTOR ||
            mbd.hasConstructorArgumentValues() || !ObjectUtils.isEmpty(args)) {
        return autowireConstructor(beanName, mbd, ctors, args);
    }

    // Preferred constructors for default construction? (默认构造的首选构造函数？)
    // 获取默认构造函数然后自动装配构造函数
    ctors = mbd.getPreferredConstructors();
    if (ctors != null) {
        return autowireConstructor(beanName, mbd, ctors, null);
    }

    // No special handling: simply use no-arg constructor.
    // 无自动装配,只有默认构造函数的处理  如果xml中有使用  如果使用过lookup-method、replace-method,则产生cglib代理类
    return instantiateBean(beanName, mbd);
}


// 从Supplier获取类的实例
protected BeanWrapper obtainFromSupplier(Supplier<?> instanceSupplier, String beanName) {
    Object instance;

    String outerBean = this.currentlyCreatedBean.get();
    // 设置一个标识正在创建的bean的值
    this.currentlyCreatedBean.set(beanName);
    try {
        instance = instanceSupplier.get();
    }
    finally {
        if (outerBean != null) {
            // 这里创建完了, 就恢复原来的值
            this.currentlyCreatedBean.set(outerBean);
        }
        else {
            // 创建完了, 如果原来没有值也要清空
            this.currentlyCreatedBean.remove();
        }
    }

    if (instance == null) {
        // 这是一种设计模式 (好像就叫空对象模式).. 这让后面的操纵变得纯净..
        instance = new NullBean();
    }
    // 包装一下  BeanWrapper也不做说明, 不是我的关注点 可以自己了解
    BeanWrapper bw = new BeanWrapperImpl(instance);
    // 使用在此工厂注册的自定义编辑器初始化给定的BeanWrapper。 为将要创建和填充bean实例的BeanWrappers调用。
    initBeanWrapper(bw);
    return bw;
}
```
`populateBean`方法分析
```
// 使用BeanDefinition中的属性值填充给BeanWrapper中的bean实例
protected void populateBean(String beanName, RootBeanDefinition mbd, @Nullable BeanWrapper bw) {
    // 一个先行的校验 确保传入的BeanWrapper不为空
    if (bw == null) {
        if (mbd.hasPropertyValues()) {
            throw new BeanCreationException(
                    mbd.getResourceDescription(), beanName, "Cannot apply property values to null instance");
        }
        else {
            // Skip property population phase for null instance.
            return;
        }
    }

    // 给所有的InstantiationAwareBeanPostProcessors提供一个在设置实例化对象属性之前修改bean状态的机会
    // 例如，这可用于支持现字段的注入操作
    boolean continueWithPropertyPopulation = true;

    // 这里先让所有的BeanPostProcessors走一轮
    if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
        for (BeanPostProcessor bp : getBeanPostProcessors()) {
            if (bp instanceof InstantiationAwareBeanPostProcessor) {
                InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
                // 基于默认提供的实现中,下面的if一直不会进入
                // postProcessAfterInstantiation这个里面可以进行一些针对实例对象的操作,例如字段注入(但默认实现中都不进行操作)
                if (!ibp.postProcessAfterInstantiation(bw.getWrappedInstance(), beanName)) {
                    continueWithPropertyPopulation = false;
                    break;
                }
            }
        }
    }

    if (!continueWithPropertyPopulation) {
        return;
    }

    // 如果有属性值就获取到属性值,否则获取null
    PropertyValues pvs = (mbd.hasPropertyValues() ? mbd.getPropertyValues() : null);

    // getResolvedAutowireMode() 用来返回已解析的自动装配状态码
    // 由于AUTOWIRE_AUTODETECT: 4已经过时, 里面还有个工作就是将AUTOWIRE_AUTODETECT解析为AUTOWIRE_CONSTRUCTOR: 2或AUTOWIRE_BY_TYPE: 3
    // AUTOWIRE_BY_TYPE(3)     表示按类型自动装配bean属性。
    // AUTOWIRE_CONSTRUCTOR(2) 表示自动装配构造函数。
    // AUTOWIRE_BY_NAME(1)     表示按名称自动装配bean属性（适用于所有bean属性设置器）

    // 如果是要按照 类型 或 名称 来自动装配bean属性
    // 是根据bean标签中的autowire属性设置的,如果不设置就是default(继承父级标签(beans的default-autowire)配置),如果上级标签也没有,则返回no(即没有外部驱动的自动装配)
    if (mbd.getResolvedAutowireMode() == AUTOWIRE_BY_NAME || mbd.getResolvedAutowireMode() == AUTOWIRE_BY_TYPE) {
        MutablePropertyValues newPvs = new MutablePropertyValues(pvs);
        // 根据名称添加基于autowire的属性值。  需要xml中的autowire="byName"属性
        if (mbd.getResolvedAutowireMode() == AUTOWIRE_BY_NAME) {
            autowireByName(beanName, mbd, bw, newPvs);
        }
        // 根据类型添加基于autowire的属性值。  需要xml中的autowire="byType"属性
        if (mbd.getResolvedAutowireMode() == AUTOWIRE_BY_TYPE) {
            autowireByType(beanName, mbd, bw, newPvs);
        }
        pvs = newPvs;
    }

    boolean hasInstAwareBpps = hasInstantiationAwareBeanPostProcessors();
    // DEPENDENCY_CHECK_NONE: 表示根本没有依赖性检查。
    // 这东西在spring2.x版本中以dependency-check的方式配置,在5.x中我还没找到它与它的替代品..
    boolean needsDepCheck = (mbd.getDependencyCheck() != AbstractBeanDefinition.DEPENDENCY_CHECK_NONE);

    // 这里是通过
    PropertyDescriptor[] filteredPds = null;
    if (hasInstAwareBpps) {
        if (pvs == null) {
            pvs = mbd.getPropertyValues();
        }
        for (BeanPostProcessor bp : getBeanPostProcessors()) {
            if (bp instanceof InstantiationAwareBeanPostProcessor) {
                InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
                // 这个指的关注的是ibp是AutowiredAnnotationBeanPostProcessor时
                PropertyValues pvsToUse = ibp.postProcessProperties(pvs, bw.getWrappedInstance(), beanName);
                if (pvsToUse == null) {
                    if (filteredPds == null) {
                        filteredPds = filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
                    }
                    // 这个指的关注的是ibp是RequiredAnnotationBeanPostProcessor时
                    pvsToUse = ibp.postProcessPropertyValues(pvs, filteredPds, bw.getWrappedInstance(), beanName);
                    if (pvsToUse == null) {
                        return;
                    }
                }
                pvs = pvsToUse;
            }
        }
    }
    // 如果有依赖性检查
    if (needsDepCheck) {
        if (filteredPds == null) {
            filteredPds = filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
        }
        checkDependencies(beanName, mbd, filteredPds, pvs);
    }

    // 应用属性值, 这里不做描述
    if (pvs != null) {
        applyPropertyValues(beanName, mbd, bw, pvs);
    }
}
```
`initializeBean`方法的分析
```
protected Object initializeBean(final String beanName, final Object bean, @Nullable RootBeanDefinition mbd) {
    if (System.getSecurityManager() != null) {
        AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
            invokeAwareMethods(beanName, bean);
            return null;
        }, getAccessControlContext());
    }
    else {
         // 这里是设置bean对一些内容感知的地方  可以自己搜索一下Aware,也挺好理解的
         // 设置BeanNameAware、BeanClassLoaderAware或BeanFactoryAware的具体内容
        invokeAwareMethods(beanName, bean);
    }

    Object wrappedBean = bean;
    if (mbd == null || !mbd.isSynthetic()) {
        // 这里默认实现按原样返回给定的bean。  有一些有具体内容的例如:ImportAwareBeanPostProcessor、ConfigurationPropertiesBindingPostProcessor有兴趣的可以自己看看
        wrappedBean = applyBeanPostProcessorsBeforeInitialization(wrappedBean, beanName);
    }

    try {
        // 让bean有机会对其所有属性进行设置,并有机会了解它拥有的bean factory(这个对象)。 
        // 这意味着检查bean是否实现了InitializingBean或定义了一个自定义init-method,并调用了必要的回调(如果有)。
        // 详情 了解 InitializingBean 和 init-method
        // 这个东西。。就是你实现InitializingBean接口然后  会去主动调用你写的afterPropertiesSet方法
        invokeInitMethods(beanName, wrappedBean, mbd);
    }
    catch (Throwable ex) {
        throw new BeanCreationException(
                (mbd != null ? mbd.getResourceDescription() : null),
                beanName, "Invocation of init method failed", ex);
    }
    if (mbd == null || !mbd.isSynthetic()) {
        // 这里是实现普通bean的aop的入口
        // 主要考虑AbstractAutoProxyCreator的实现
        // AbstractAutoProxyCreator是AspectJAwareAdvisorAutoProxyCreator的父类
        wrappedBean = applyBeanPostProcessorsAfterInitialization(wrappedBean, beanName);
    }

    return wrappedBean;
}

@Override
public Object applyBeanPostProcessorsAfterInitialization(Object existingBean, String beanName)
        throws BeansException {

    Object result = existingBean;
    for (BeanPostProcessor processor : getBeanPostProcessors()) {
        Object current = processor.postProcessAfterInitialization(result, beanName);
        if (current == null) {
            return result;
        }
        result = current;
    }
    return result;
}
```
最后我们了解一下如何使用`AspectJAwareAdvisorAutoProxyCreator`来实现代理。 
在上文中提到`applyBeanPostProcessorsAfterInitialization`方法是aop的入口,但是该类并未实现该方法,只能去父类中找。
最终在`AbstractAutoProxyCreator`类中可以找到实习。
```
@Override
public Object postProcessAfterInitialization(@Nullable Object bean, String beanName) {
    if (bean != null) {
        Object cacheKey = getCacheKey(bean.getClass(), beanName);
        if (!this.earlyProxyReferences.contains(cacheKey)) {
            return wrapIfNecessary(bean, beanName, cacheKey);
        }
    }
    return bean;
}

// 上文中有个 () -> getEarlyBeanReference 的地方 getEarlyBeanReference方法中也有对该方法的调用
protected Object wrapIfNecessary(Object bean, String beanName, Object cacheKey) {
    if (StringUtils.hasLength(beanName) && this.targetSourcedBeans.contains(beanName)) {
        return bean;
    }
    if (Boolean.FALSE.equals(this.advisedBeans.get(cacheKey))) {
        return bean;
    }
    if (isInfrastructureClass(bean.getClass()) || shouldSkip(bean.getClass(), beanName)) {
        this.advisedBeans.put(cacheKey, Boolean.FALSE);
        return bean;
    }

    // Create proxy if we have advice. (如果有advice(配置), 就创建代理)
    Object[] specificInterceptors = getAdvicesAndAdvisorsForBean(bean.getClass(), beanName, null);
    if (specificInterceptors != DO_NOT_PROXY) {
        // 如果我们有合格的Advisor集合
        this.advisedBeans.put(cacheKey, Boolean.TRUE);

        // 创建代理
        Object proxy = createProxy(
                bean.getClass(), beanName, specificInterceptors, new SingletonTargetSource(bean));
        this.proxyTypes.put(cacheKey, proxy.getClass());
        return proxy;
    }

    this.advisedBeans.put(cacheKey, Boolean.FALSE);
    return bean;
}
```
这里稍微深入看一下`getAdvicesAndAdvisorsForBean`方法,进入到`AbstractAdvisorAutoProxyCreator.class`的实现中
```
@Override
@Nullable
protected Object[] getAdvicesAndAdvisorsForBean(
        Class<?> beanClass, String beanName, @Nullable TargetSource targetSource) {

    // 找到有资格的Advisor集合,其实就是通过切面的表达式(转为MethodMatcher)进行匹配上的
    List<Advisor> advisors = findEligibleAdvisors(beanClass, beanName);
    if (advisors.isEmpty()) {
        return DO_NOT_PROXY;
    }
    // 将集合转为数组
    return advisors.toArray();
}

// 继续深入,会看到这个方法
protected List<Advisor> findEligibleAdvisors(Class<?> beanClass, String beanName) {
    // 获取所有的Advisor集合
    List<Advisor> candidateAdvisors = findCandidateAdvisors();
    // 判断有哪个是合格的
    List<Advisor> eligibleAdvisors = findAdvisorsThatCanApply(candidateAdvisors, beanClass, beanName);
    extendAdvisors(eligibleAdvisors);
    if (!eligibleAdvisors.isEmpty()) {
        eligibleAdvisors = sortAdvisors(eligibleAdvisors);
    }
    return eligibleAdvisors;
}

// 我们最后将目光放到后如下方法
public static List<Advisor> findAdvisorsThatCanApply(List<Advisor> candidateAdvisors, Class<?> clazz) {
    if (candidateAdvisors.isEmpty()) {
        return candidateAdvisors;
    }
    List<Advisor> eligibleAdvisors = new ArrayList<>();
    for (Advisor candidate : candidateAdvisors) {
        if (candidate instanceof IntroductionAdvisor && canApply(candidate, clazz)) {
            eligibleAdvisors.add(candidate);
        }
    }
    boolean hasIntroductions = !eligibleAdvisors.isEmpty();
    for (Advisor candidate : candidateAdvisors) {
        // 我们切面的advisor都是AspectJPointcutAdvisor.class,没有与IntroductionAdvisor有关的
        if (candidate instanceof IntroductionAdvisor) {
            // already processed
            continue;
        }
        // 这个canApply里面就是那 MethodMatcher 去匹配,具体操作我也不会(没看过)
        if (canApply(candidate, clazz, hasIntroductions)) {
            eligibleAdvisors.add(candidate);
        }
    }
    // 这里就返回合格的Advisor集合
    return eligibleAdvisors;
}
```
继续分析`createProxy`方法
```
// 当前位于AbstractAutoProxyCreator.class中
/**
 * Create an AOP proxy for the given bean.
 *
 * @param beanClass the class of the bean
 * @param beanName the name of the bean
 * @param specificInterceptors the set of interceptors that is specific to this bean (may be empty, but not null)
 * @param targetSource the TargetSource for the proxy, already pre-configured to access the bean
 *
 * @return the AOP proxy for the bean
 * @see #buildAdvisors
 */
protected Object createProxy(Class<?> beanClass, @Nullable String beanName,
            @Nullable Object[] specificInterceptors, TargetSource targetSource) {

    if (this.beanFactory instanceof ConfigurableListableBeanFactory) {
        // Expose the given target class for the specified bean, if possible.
        AutoProxyUtils.exposeTargetClass((ConfigurableListableBeanFactory) this.beanFactory, beanName, beanClass);
    }

    // 创建ProxyFactory(负责具体的创建代理)并进行一些额外的操作(我没注意这些操作到底是干什么的).
    ProxyFactory proxyFactory = new ProxyFactory();
    proxyFactory.copyFrom(this);

    if (!proxyFactory.isProxyTargetClass()) {
        if (shouldProxyTargetClass(beanClass, beanName)) {
            // 设置是否直接代理目标类，而不是仅代理特定接口。 默认为"false"。
            // 将其设置为"true"以强制代理TargetSource的公开目标类。 如果该目标类是接口，则将为给定接口创建JDK代理。 
            // 如果该目标类是任何其他类，则将为给定类创建CGLIB代理。
            proxyFactory.setProxyTargetClass(true);
        }
        else {
            evaluateProxyInterfaces(beanClass, proxyFactory);
        }
    }

    // 将当前bean适合的advice，重新封装下，封装为Advisor类，然后添加到ProxyFactory中
    Advisor[] advisors = buildAdvisors(beanName, specificInterceptors);
    proxyFactory.addAdvisors(advisors);
    // 设置要代理的目标
    proxyFactory.setTargetSource(targetSource);
    // 这个方法也是个模板方法模式的例子, 空实现不关注
    customizeProxyFactory(proxyFactory);

    proxyFactory.setFrozen(this.freezeProxy);
    if (advisorsPreFiltered()) {
        proxyFactory.setPreFiltered(true);
    }

    // 经过一系列对ProxyFactory的操作后, 开始创建代理, 注意下面这个getProxy方法
    return proxyFactory.getProxy(getProxyClassLoader());
}
```
开始分析`getProxy`方法
```
// 该方法位于ProxyFactory.class
public Object getProxy(@Nullable ClassLoader classLoader) {
    return createAopProxy().getProxy(classLoader);
}

// 该方法位于ProxyCreatorSupport.class
protected final synchronized AopProxy createAopProxy() {
    if (!this.active) {
        activate();
    }
    return getAopProxyFactory().createAopProxy(this);
}

// 该方法位于DefaultAopProxyFactory.class
@Override
public AopProxy createAopProxy(AdvisedSupport config) throws AopConfigException {
    // config.isOptimize()是否使用优化的代理策略  在ProxyConfig.class默认为false
    // config.isProxyTargetClass() 是否目标类本身被代理而不是目标类的接口
    // hasNoUserSuppliedProxyInterfaces() 是否存在代理接口
    if (config.isOptimize() || config.isProxyTargetClass() || hasNoUserSuppliedProxyInterfaces(config)) {
        Class<?> targetClass = config.getTargetClass();
        if (targetClass == null) {
            throw new AopConfigException("TargetSource cannot determine target class: " +
                    "Either an interface or a target is required for proxy creation.");
        }
        // 如果目标类是接口或...
        if (targetClass.isInterface() || Proxy.isProxyClass(targetClass)) {
            return new JdkDynamicAopProxy(config);
        }
        // 其他情况
        return new ObjenesisCglibAopProxy(config);
    }
    else {
        return new JdkDynamicAopProxy(config);
    }
}
```

代理模式的描述和示例 -> [跳转地址](https://github.com/2937735094/java-design-patterns/tree/master/src/proxy)
JDK Dynamic Proxy： 只能对接口进行代理  
CGLIB： 不受必须实现接口的限制,但对于final方法无法代理

到这里我们已经得知`createAopProxy()`是选择一种代理方式,当然你可以通过设置强制选择..

我们回到`getProxy(ClassLoader)`方法,现在继续看后面的`getProxy(classLoader)`,这个其实就是具体的获取代理的方法了。
我以前学设计模式的时候写过简单了例子,[跳转地址](https://github.com/2937735094/java-design-patterns/tree/master/src/proxy)
至此AspectJAwareAdvisorAutoProxyCreator的调用时机和具体操作也说完了(即aop也说完了)

最后的`AbstractApplicationContext#finishRefresh()`方法就不提了

上述全部,说了点上面?
主要关注点:
1.首先创建BeanFactory
2.其次通过不同种的ApplicationContext实现解读不同的元数据(解读成BeanDefinition)并注册
3.然后实例化BeanDefinition(这时候还没有赋值)
4.填充数据
5.判断是否创建代理
