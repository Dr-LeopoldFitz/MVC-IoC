# IoC控制反转 & DI依赖注入

[TOC]


## 概念

### 控制反转

1. 之前在Servlet中，创建`service`对象 ，` FruitService fruitService = new FruitServiceImpl();`
   这句话如果出现在`servlet`中的某个方法内部，那么这个`fruitService`的作用域（生命周期）应该就是这个方法级别；
   如果这句话出现在`servlet`的类中，也就是说`fruitService`是一个成员变量，那么这个`fruitService`的作用域(生命周期)应该就是这个`servlet`实例级别
2. 之后在`applicationContext.xml`中定义了这个`fruitService`。然后通过解析XML，产生`fruitService`实例，存放在`beanMap`中，这个`beanMap`在一个`BeanFactory`中,`beanMap`销毁时`fruitService`才会销毁

此时，我们相当于转移（改变）了之前的service实例、dao实例等等他们的生命周期。控制权从程序员转移到BeanFactory。这个现象称之为**控制反转**

### 依赖注入

1. 之前在控制层出现代码：`FruitService fruitService = new FruitServiceImpl()；`
   那么，控制层和`service`层存在耦合。
2. 之后，我们将代码修改成`FruitService fruitService = null ;`
   然后，在配置文件中配置:

```xml
   <bean id="fruit" class="FruitController">
      <property name="fruitService" ref="fruitService"/>
   </bean>
```

此xml表明`FruitController`需要`Service`，那么IOC文件在解析这个文件的时候寻找到`ref`对应的实例 ，注入到`FruitController`中。

即以前是在控制层出现代码：`FruitService fruitService = new FruitServiceImpl()；` 即程序员主动获取；引入配置文件后变为注入。

即依赖关系以前是程序员主动获取并满足的，引入配置文件后是靠配置文件解析，容器帮我们将依赖条件注入进去(使用反射技术)





## 实例引入

实现代码仓库地址: `git@github.com:Dr-LeopoldFitz/MVC-IoC.git`



1. 对于`FruitController`类

```java
//private FruitService fruitService = new FruitServiceImpl();
//使用new表示Controller和下层的FruitServiceImpl有关系了，当下层改动时本层及以上会受影响
private FruitService fruitService = null ;
//改成null 若删除FruitServiceImpl不会报错，但此层因为空指针无法运行
```

2. 要解决空指针问题，引入配置文件`src/applicationContext.xml`，并在`src/applicationContext.xml`中加标签

```xml
<?xml version="1.0" encoding="utf-8"?>

<beans>

    <bean id="fruitDAO" class="com.hive.fruit.dao.impl.FruitDAOImpl"/>
    <bean id="fruitService" class="com.hive.fruit.service.impl.FruitServiceImpl"/>
    <bean id="fruit" class="com.hive.fruit.controllers.FruitController"/>

</beans>
```

配置文件首先要描述有哪些组件，其次要描述组件和组件之间的**依赖关系**。需要描述组件关系时要将该组件单标签改为双标签：

```xml
<?xml version="1.0" encoding="utf-8"?>

<beans>

    <bean id="fruitDAO" class="com.hive.fruit.dao.impl.FruitDAOImpl"/>
    
    <bean id="fruitService" class="com.hive.fruit.service.impl.FruitServiceImpl">
        <property name="fruitDAO" ref="fruitDAO"/>
    </bean>
    
    <!-- property标签用来表示属性(自定名)；name表示属性名；ref表示引用其他bean的id值-->
    <!--
			第8行ref中的fruitDAO引用某一个bean的id，和第5行中的fruitDAO是对应的，
			即引用的是com.hive.fruit.dao.impl.FruitDAOImpl这个bean

			第8行的property中的DAO指的是第7行class="com.hive.fruit.service.impl.FruitServiceImpl"中的
			某一个property(属性名)叫fruitDAO
			此时FruitServiceImpl和FruitDAO层的关系建好了
	-->
    <!--
            fruitService代表FruitServiceImpl，他需要FruitDAO接口
            fruit代表FruitController，他需要FruitService接口
  	-->
    
    <bean id="fruit" class="com.hive.fruit.controllers.FruitController">
        <property name="fruitService" ref="fruitService"/>
    </bean>
    
</beans>
```

每个 bean 对应一个组件，系统启动时，预期达到让系统将这三个组件准备好放在一个容器里的效果，在`DispatcherServlet`类对象内哪里需要的时候就直接从beanMap容器中取出

```java
public class FruitServiceImpl implements FruitService {
    private FruitDAO fruitDAO = null ;
}
public class FruitController {
    private FruitService fruitService = null ;
}

```



3. 实现一个接口`BeanFactory`，可以根据 id 获取 id 后面跟的对象

如：`<bean id="fruitDAO" class="com.hive.fruit.dao.impl.FruitDAOImpl"/>` 根据id`fruitDAO`获取到一个实例对象`com.hive.fruit.dao.impl.FruitDAOImpl`

```java
package com.hive.myssm.io;

public interface BeanFactory {
    Object getBean(String id); //根据id获取到Bean对象
}

```

4. 再写一个实现类`ClassPathXmlApplicationContext`实现`BeanFactory`接口

```java
package com.hive.myssm.io;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class ClassPathXmlApplicationContext implements BeanFactory {

    //需要一个Map容器
    private Map<String, Object> beanMap = new HashMap<>();

    public ClassPathXmlApplicationContext() {
        //将原DispatchServlet类内的init代码全部粘贴进try块即可即可
        try {
            //加载配置文件
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream("applicationContext.xml");
            //1.创建DocumentBuilderFactory
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            //2.创建DocumentBuilder对象
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            //3.创建Document对象
            Document document = documentBuilder.parse(inputStream);

            //4.获取所有的bean节点，根据每一个Bean节点创建出对应的obj对象并存入Map
            NodeList beanNodeList = document.getElementsByTagName("bean");
            for (int i = 0; i < beanNodeList.getLength(); i++) {
                Node beanNode = beanNodeList.item(i);
                if (beanNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element beanElement = (Element) beanNode;
                    String beanId = beanElement.getAttribute("id");
                    String className = beanElement.getAttribute("class");
                    Class beanClass = Class.forName(className);
                    //创建bean实例
                    Object beanObj = beanClass.newInstance();
                    //将bean实例对象保存到map容器中
                    beanMap.put(beanId, beanObj);
                    //到目前为止，此处需要注意的是，bean和bean之间的依赖关系还没有设置，
                    // 只是创建了xml配置文件里3个bean对应的3个实例
                }
            }
            //5.组装bean之间的依赖关系
            for (int i = 0; i < beanNodeList.getLength(); i++) { //遍历每一组<bean></bean>
                Node beanNode = beanNodeList.item(i);
                if (beanNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element beanElement = (Element) beanNode; //一个beanElement对应一组<bean></bean>
                    String beanId = beanElement.getAttribute("id");
                    NodeList beanChildNodeList = beanElement.getChildNodes(); //获取bean的所有子节点
                    for (int j = 0; j < beanChildNodeList.getLength(); j++) {
                        Node beanChildNode = beanChildNodeList.item(j);
                        //保证是元素节点且NodeName为property
                        if (beanChildNode.getNodeType() == Node.ELEMENT_NODE && "property".equals(beanChildNode.getNodeName())) {
                            //强制类型转换为Element以调用getAttribute()
                            Element propertyElement = (Element) beanChildNode;
                            String propertyName = propertyElement.getAttribute("name");
                            String propertyRef = propertyElement.getAttribute("ref");

                            //1) 找到propertyRef对应的实例 即通过ref找到对应的对象实例，给该对象的property赋值
                            Object refObj = beanMap.get(propertyRef);

                            //2) 将refObj设置到当前bean对应的实例的property属性上去
                            Object beanObj = beanMap.get(beanId);
                            Class beanClazz = beanObj.getClass();
                            Field propertyField = beanClazz.getDeclaredField(propertyName);
                            propertyField.setAccessible(true);
                            //将beanObj的依赖项(refObj的实例)赋给beanObj内成员变量propertyField
                            propertyField.set(beanObj, refObj);
                            /*
                             * 本例中即 分别：
                             * 将beanMap容器中已生成的FruitService类的实例对象 赋值给 FruitController类内
                             * 的FruitService类的成员变量fruitService(初始为null)
                             *
                             * 将beanMap容器中已生成的FruitDAO类的实例对象赋值给的
                             * FruitDAO类的成员变量fruitDAO(初始为null)
                             *
                             * 这样不必程序员在FruitController和FruitServiceImpl类的实例对象中
                             * 都再手动生成一次依赖的对象，且每次需要用FruitController或FruitServiceImpl类的
                             * 实例对象时直接从中央控制器DispatcherServlet类的实例中的beanMap容器中取已生成的即可
                             * */
                        }
                    }
                }
            }
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }


    @Override
    public Object getBean(String id) {
        return beanMap.get(id);
    }
}
```



**注意：**

对于一组`<bean></bean>` 

```xml
<bean id="fruitService" class="com.hive.fruit.service.impl.FruitServiceImpl">
	<property name="fruitDAO" ref="fruitDAO"/>
</bean>
```

`property`属于`<bean></bean>`的子节点，这个`<bean>`里面有3个子节点
两个**空白节点**(红、蓝)或叫**文本节点**，一个**元素节点**property(黄色)  元素节点没有value，文本节点有value
![在这里插入图片描述](https://img-blog.csdnimg.cn/c5210024ba8b46088e70c9e4b10d2994.png)


```xml
<sname>hive</sname>  <!--有两个结点  sname为元素节点  hive叫文本节点 -->
```

`Node`为接口，表示节点
	`Element`是`Node`的子接口，表示元素节点
	`Text`是 `Node`子接口，表示文本节点



5. BeanFactory实际调试信息：如图，配置文件中预置的3个`<bean>`标签被成功注入beanMap容器

![在这里插入图片描述](https://img-blog.csdnimg.cn/eb3ad6aad8df44a2bdf2af5bdcadaae4.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBAQWx2ZXVz,size_20,color_FFFFFF,t_70,g_se,x_16)





## 核心代码

核心代码 `com.hive.myssm.io.ClassPathXmlApplicationContext.java`：

```java
//获取所有的bean节点，根据每一个Bean节点创建出对应的obj对象并存入Map
NodeList beanNodeList = document.getElementsByTagName("bean");
for(int i = 0 ; i<beanNodeList.getLength() ; i++){
    Node beanNode = beanNodeList.item(i);
    if(beanNode.getNodeType() == Node.ELEMENT_NODE){
        Element beanElement = (Element)beanNode ;
        String beanId =  beanElement.getAttribute("id");
        String className = beanElement.getAttribute("class");
        Class beanClass = Class.forName(className);
        //创建bean实例
        Object beanObj = beanClass.newInstance() ;
        //将bean实例对象保存到map容器中
        beanMap.put(beanId , beanObj) ;
        //到目前为止，此处需要注意的是，bean和bean之间的依赖关系还没有设置，
        // 只是创建了xml配置文件里3个bean对应的3个实例
    }
}

//组装bean之间的依赖关系
for(int i = 0 ; i<beanNodeList.getLength() ; i++){ //遍历每一组<bean></bean>
    Node beanNode = beanNodeList.item(i);
    if(beanNode.getNodeType() == Node.ELEMENT_NODE) {
        Element beanElement = (Element) beanNode; //一个beanElement对应一组<bean></bean>
        String beanId = beanElement.getAttribute("id");
        NodeList beanChildNodeList = beanElement.getChildNodes(); //获取bean的所有子节点
        for (int j = 0; j < beanChildNodeList.getLength() ; j++) {
            Node beanChildNode = beanChildNodeList.item(j);
            //保证是元素节点且NodeName为property
            if(beanChildNode.getNodeType()==Node.ELEMENT_NODE && "property".equals(beanChildNode.getNodeName())){
                //强制类型转换为Element以调用getAttribute()
                Element propertyElement = (Element) beanChildNode;
                String propertyName = propertyElement.getAttribute("name");
                String propertyRef = propertyElement.getAttribute("ref");

                //1) 找到propertyRef对应的实例 即通过ref找到对应的对象实例，给该对象的property赋值
                Object refObj = beanMap.get(propertyRef);

                //2) 将refObj设置到当前bean对应的实例的property属性上去
                Object beanObj = beanMap.get(beanId);
                Class beanClazz = beanObj.getClass();
                Field propertyField = beanClazz.getDeclaredField(propertyName);
                propertyField.setAccessible(true);
                //将beanObj的依赖项(refObj的实例)赋给beanObj内成员变量propertyField
                propertyField.set(beanObj,refObj);
                /*
                * 本例中即 分别：
                * 将beanMap容器中已生成的FruitService类的实例对象 赋值给 FruitController类内
                * 的FruitService类的成员变量fruitService(初始为null)
                *
                * 将beanMap容器中已生成的FruitDAO类的实例对象赋值给的
                * FruitDAO类的成员变量fruitDAO(初始为null)
                * 
                * 这样不必程序员在FruitController和FruitServiceImpl类的实例对象中
                * 每次都手动生成依赖的对象
                * */
            }
        }
    }
}
```

**注意**：

`DispatcherServlet.java`代码中使用到`Parameter.getName()`来获取形参实际名称，从JDK8开始可以获取到。
记得要改变 设置-构建执行部署-编译器-Java编译器-附加命令行参数：`-parameters` 表示Java虚拟机在编译的时候得到的class文件里附带形参的实际名称，否则将空指针报错。


本例是MVC IoC DI 的一个简单手动实现，理解后对相关框架理解大有帮助。