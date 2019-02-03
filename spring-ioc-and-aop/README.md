事先声明: 以下所有内容都是基于xml的配置的方式进行讲解的,为了降低理解IOC和AOP的难度。Spring Boot加载Spring的方式以后会补充Spring Boot将解析中。  
阅读建议: 阅读者要对Spring有一定的认知。 现在的spring源码规模太大了,如果想要自己学的话,可以下载如下项目进行摸索: https://github.com/code4craft/tiny-spring

源码分析将从如下入口开始:
```
// 位于demo.src.test.java.com.example.demo.context.ApplicationContextTest#analysis()
ApplicationContext applicationContext = new ClassPathXmlApplicationContext(new String[] {"spring-config.xml"}, true, null);
HelloService helloService = (HelloService) applicationContext.getBean("helloService");
helloService.sayHello();
```
首先我们先看第一行 `ApplicationContext applicationContext = new ClassPathXmlApplicationContext(...);` 的操作。
通过引用可以看出这是一个创建ApplicationContext的过程。代码通过调用ClassPathXmlApplicationContext的构造函数来返回一个ApplicationContext。  

这里我先引入一张类关系图(该图片是我简化过后的,原图太复杂了...)  
![ApplicationContext关系图](images/ClassPathXmlApplicationContext类关系图.png)  

该图片是我处理过后的ClassPathXmlApplicationContext的类关系图。可以使用IDEA的**Ctrl + Alt + Shift + U**来操作出关系图。  
接上原来的话,我们得知ApplicationContext是通过ClassPathXmlApplicationContext的构造函数返回的,图中显示ApplicationContext和ClassPathXmlApplicationContext有继承关系,那么我们跟进构造函数中看一下。
```
/**
* Create a new ClassPathXmlApplicationContext with the given parent, loading the definitions from the given XML files.
*/
public ClassPathXmlApplicationContext(String[] configLocations, 
      boolean refresh, @Nullable ApplicationContext parent) throws BeansException {
  // 通过注释、参数名称和我们传递的参数大致可以看出参数都是什么意思。我们确实没有父级别的ApplicationContext,所以parent穿的为null。
  // 这里就不去跟踪了,和我们这里讲的内容无关。(最终它是 设置一个parent属性 并 将环境和并.) 
  super(parent); 
  // 也不跟了。(就是将传入的configLocations传递给该对象的`configLocations`属性)  
  setConfigLocations(configLocations); 
  if (refresh) { // 可能会有疑惑`refresh`不能为false吗? 当然能,如果为false,上下文的构建就无法进行下去,最初3行程序执行也就会报错了
		refresh();
	}
}
``` 
重点是`refresh()`方法。下面跟进去看一下.  
```
public void refresh() throws BeansException, IllegalStateException {
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

