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
package org.apache.dubbo.config.spring.context.properties;

import org.apache.dubbo.config.AbstractConfig;
import org.apache.dubbo.config.spring.util.PropertySourcesUtils;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.validation.DataBinder;

import java.util.Map;

/**
 * Default {@link DubboConfigBinder} implementation based on Spring {@link DataBinder}
 */
public class DefaultDubboConfigBinder extends AbstractDubboConfigBinder {

    @Override
    public <C extends AbstractConfig> void bind(String prefix, C dubboConfig) {
        // 将 dubboConfig 包装成 DataBinder 对象
        DataBinder dataBinder = new DataBinder(dubboConfig);
        // Set ignored*
        // 设置响应的 ignored* 属性
        dataBinder.setIgnoreInvalidFields(isIgnoreInvalidFields());
        dataBinder.setIgnoreUnknownFields(isIgnoreUnknownFields());
        // Get properties under specified prefix from PropertySources
        // 获得 prefix 开头的配置属性
        Map<String, Object> properties = PropertySourcesUtils.getSubProperties(getPropertySources(), prefix);
        // Convert Map to MutablePropertyValues
        // 创建 MutablePropertyValues 对象
        MutablePropertyValues propertyValues = new MutablePropertyValues(properties);
        // Bind
        // 绑定配置属性到 dubboConfig 中
        dataBinder.bind(propertyValues);
    }

}