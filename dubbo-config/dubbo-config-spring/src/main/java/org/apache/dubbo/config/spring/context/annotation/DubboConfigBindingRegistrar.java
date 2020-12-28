/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.config.spring.context.annotation;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.dubbo.config.AbstractConfig;
import org.apache.dubbo.config.spring.beans.factory.annotation.DubboConfigBindingBeanPostProcessor;
import org.apache.dubbo.config.spring.util.PropertySourcesUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.springframework.beans.factory.support.BeanDefinitionBuilder.rootBeanDefinition;

/**
 * {@link AbstractConfig Dubbo Config} binding Bean registrar
 *
 * @see EnableDubboConfigBinding
 * @see DubboConfigBindingBeanPostProcessor
 * @since 2.5.8
 */
public class DubboConfigBindingRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {

    private final Log log = LogFactory.getLog(getClass());

    private ConfigurableEnvironment environment;

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        // 获得 @EnableDubboConfigBinding 注解
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(importingClassMetadata.getAnnotationAttributes(EnableDubboConfigBinding.class.getName()));
        // 注册配置对应的 Bean Definition 对象
        registerBeanDefinitions(attributes, registry);
    }

    protected void registerBeanDefinitions(AnnotationAttributes attributes, BeanDefinitionRegistry registry) {
        // 获得 prefix 属性
        String prefix = environment.resolvePlaceholders(attributes.getString("prefix")); // 因为，有可能有占位符，所以要解析。
        // 获得 type 属性，即 AbstractConfig 的实现类
        Class<? extends AbstractConfig> configClass = attributes.getClass("type");
        // 获得 multiple 属性
        boolean multiple = attributes.getBoolean("multiple");
        // 注册 Dubbo Config Bean 对象
        registerDubboConfigBeans(prefix, configClass, multiple, registry);
    }

    private void registerDubboConfigBeans(String prefix, Class<? extends AbstractConfig> configClass, boolean multiple,
                                          BeanDefinitionRegistry registry) {
        // 获得 prefix 开头的配置属性
        Map<String, Object> properties = PropertySourcesUtils.getSubProperties(environment.getPropertySources(), prefix);
        // 如果配置属性为空，则无需创建
        if (CollectionUtils.isEmpty(properties)) {
            if (log.isDebugEnabled()) {
                log.debug("There is no property for binding to dubbo config class [" + configClass.getName()
                        + "] within prefix [" + prefix + "]");
            }
            return;
        }
        // 获得配置属性对应的 Bean 名字的集合
        Set<String> beanNames = multiple ? resolveMultipleBeanNames(properties) :
                Collections.singleton(resolveSingleBeanName(properties, configClass, registry));
        // 遍历 beanNames 数组，逐个注册
        for (String beanName : beanNames) {
            // 注册 Dubbo Config Bean 对象
            registerDubboConfigBean(beanName, configClass, registry);
            // 注册 Dubbo Config 对象对应的 DubboConfigBindingBeanPostProcessor 对象
            registerDubboConfigBindingBeanPostProcessor(prefix, beanName, multiple, registry);
        }
    }

    private void registerDubboConfigBean(String beanName, Class<? extends AbstractConfig> configClass,
                                         BeanDefinitionRegistry registry) {
        // 创建 BeanDefinitionBuilder 对象
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.rootBeanDefinition(configClass);
        // 获得 AbstractBeanDefinition 对象
        AbstractBeanDefinition beanDefinition = builder.getBeanDefinition();
        // 注册到 registry 中
        registry.registerBeanDefinition(beanName, beanDefinition);
        if (log.isInfoEnabled()) {
            log.info("The dubbo config bean definition [name : " + beanName + ", class : " + configClass.getName() + "] has been registered.");
        }
    }

    private void registerDubboConfigBindingBeanPostProcessor(String prefix, String beanName, boolean multiple,
                                                             BeanDefinitionRegistry registry) {
        // 创建 BeanDefinitionBuilder 对象
        Class<?> processorClass = DubboConfigBindingBeanPostProcessor.class;
        BeanDefinitionBuilder builder = rootBeanDefinition(processorClass);
        // 添加构造方法的参数为 actualPrefix 和 beanName 。即，创建 DubboConfigBindingBeanPostProcessor 对象，需要这两个构造参数
        String actualPrefix = multiple ? PropertySourcesUtils.normalizePrefix(prefix) + beanName : prefix;
        builder.addConstructorArgValue(actualPrefix).addConstructorArgValue(beanName);
        // 获得 AbstractBeanDefinition 对象
        AbstractBeanDefinition beanDefinition = builder.getBeanDefinition();
        // 设置 role 属性
        beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
        // 注册到 registry 中
        BeanDefinitionReaderUtils.registerWithGeneratedName(beanDefinition, registry);
        if (log.isInfoEnabled()) {
            log.info("The BeanPostProcessor bean definition [" + processorClass.getName() + "] for dubbo config bean [name : " + beanName + "] has been registered.");
        }
    }

    @Override
    public void setEnvironment(Environment environment) {
        Assert.isInstanceOf(ConfigurableEnvironment.class, environment);
        this.environment = (ConfigurableEnvironment) environment;
    }

    // 例如：dubbo.application.${beanName}.name=dubbo-demo-annotation-provider
    private Set<String> resolveMultipleBeanNames(Map<String, Object> properties) {
        Set<String> beanNames = new LinkedHashSet<String>();
        for (String propertyName : properties.keySet()) {
            // 获取上述示例的 ${beanName} 字符串
            int index = propertyName.indexOf(".");
            if (index > 0) {
                String beanName = propertyName.substring(0, index);
                beanNames.add(beanName);
            }
        }
        return beanNames;
    }

    // 例如：dubbo.application.name=dubbo-demo-annotation-provider
    private String resolveSingleBeanName(Map<String, Object> properties, Class<? extends AbstractConfig> configClass,
                                         BeanDefinitionRegistry registry) {
        // 获得 Bean 的名字
        String beanName = (String) properties.get("id");
        // 如果定义，基于 Spring 提供的机制，生成对应的 Bean 的名字。例如说：org.apache.dubbo.config.ApplicationConfig#0
        if (!StringUtils.hasText(beanName)) {
            BeanDefinitionBuilder builder = BeanDefinitionBuilder.rootBeanDefinition(configClass);
            beanName = BeanDefinitionReaderUtils.generateBeanName(builder.getRawBeanDefinition(), registry);
        }
        return beanName;

    }

}
