package com.hive.myssm.ioc;

import com.hive.myssm.util.StringUtil;
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

    //配置文件默认设置为applicationContext.xml
    private String path = "applicationContext.xml" ;
    public ClassPathXmlApplicationContext(){
        //无参的构造函数调用有参的构造函数
        this("applicationContext.xml");
    }


    public ClassPathXmlApplicationContext(String path) {

        if(StringUtil.isEmpty(path)){
            throw new RuntimeException("IOC容器的配置文件没有指定...");
        }

        //将原DispatchServlet类内的init代码全部粘贴进try块即可即可
        try {
            //加载配置文件
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(path);
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
