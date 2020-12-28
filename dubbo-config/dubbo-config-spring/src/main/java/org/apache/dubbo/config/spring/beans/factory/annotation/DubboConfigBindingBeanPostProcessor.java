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
package org.apache.dubbo.config.spring.beans.factory.annotation;

import org.apache.dubbo.common.utils.Assert;
import org.apache.dubbo.config.AbstractConfig;
import org.apache.dubbo.config.spring.context.annotation.DubboConfigBindingRegistrar;
import org.apache.dubbo.config.spring.context.annotation.EnableDubboConfigBinding;
import org.apache.dubbo.config.spring.context.properties.DefaultDubboConfigBinder;
import org.apache.dubbo.config.spring.context.properties.DubboConfigBinder;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.env.Environment;

/**
 * Dubbo Config Binding {@link BeanPostProcessor}
 *
 * @see EnableDubboConfigBinding
 * @see DubboConfigBindingRegistrar
 * @since 2.5.8
 */
public class DubboConfigBindingBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware, InitializingBean {

    private final Log log = LogFactory.getLog(getClass());

    /**
     * The prefix of Configuration Properties
     *
     * 配置属性的前缀
     */
    private final String prefix;

    /**
     * Binding Bean Name
     *
     * Bean 的名字
     */
    private final String beanName;

    private DubboConfigBinder dubboConfigBinder;

    private ApplicationContext applicationContext;

    /**
     * 是否忽略位置的属性
     */
    private boolean ignoreUnknownFields = true;

    /**
     * 是否忽略类型不对的属性
     */
    private boolean ignoreInvalidFields = true;

    /**
     * @param prefix   the prefix of Configuration Properties
     * @param beanName the binding Bean Name
     */
    public DubboConfigBindingBeanPostProcessor(String prefix, String beanName) {
        Assert.notNull(prefix, "The prefix of Configuration Properties must not be null");
        Assert.notNull(beanName, "The name of bean must not be null");
        this.prefix = prefix;
        this.beanName = beanName;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        // 判断必须是 beanName ，并且是 AbstractConfig 类型
        if (beanName.equals(this.beanName) && bean instanceof AbstractConfig) {
            AbstractConfig dubboConfig = (AbstractConfig) bean;
            // 设置属性到 dubboConfig 中
            dubboConfigBinder.bind(prefix, dubboConfig);
            if (log.isInfoEnabled()) {
                log.info("The properties of bean [name : " + beanName + "] have been binding by prefix of " + "configuration properties : " + prefix);
            }
        }
        return bean;
    }

    public boolean isIgnoreUnknownFields() {
        return ignoreUnknownFields;
    }

    public void setIgnoreUnknownFields(boolean ignoreUnknownFields) {
        this.ignoreUnknownFields = ignoreUnknownFields;
    }

    public boolean isIgnoreInvalidFields() {
        return ignoreInvalidFields;
    }

    public void setIgnoreInvalidFields(boolean ignoreInvalidFields) {
        this.ignoreInvalidFields = ignoreInvalidFields;
    }

    public DubboConfigBinder getDubboConfigBinder() {
        return dubboConfigBinder;
    }

    public void setDubboConfigBinder(DubboConfigBinder dubboConfigBinder) {
        this.dubboConfigBinder = dubboConfigBinder;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // 获得（创建）DubboConfigBinder 对象
        if (dubboConfigBinder == null) {
            try {
                dubboConfigBinder = applicationContext.getBean(DubboConfigBinder.class);
            } catch (BeansException ignored) {
                if (log.isDebugEnabled()) {
                    log.debug("DubboConfigBinder Bean can't be found in ApplicationContext.");
                }
                // Use Default implementation
                dubboConfigBinder = createDubboConfigBinder(applicationContext.getEnvironment());
            }
        }
        // 设置 ignoreUnknownFields、ignoreInvalidFields 属性
        dubboConfigBinder.setIgnoreUnknownFields(ignoreUnknownFields);
        dubboConfigBinder.setIgnoreInvalidFields(ignoreInvalidFields);
    }

    /**
     * Create {@link DubboConfigBinder} instance.
     *
     * @param environment
     * @return {@link DefaultDubboConfigBinder}
     */
    protected DubboConfigBinder createDubboConfigBinder(Environment environment) {
        // 创建 DefaultDubboConfigBinder 对象
        DefaultDubboConfigBinder defaultDubboConfigBinder = new DefaultDubboConfigBinder();
        // 设置 environment 属性
        defaultDubboConfigBinder.setEnvironment(environment);
        return defaultDubboConfigBinder;
    }

}