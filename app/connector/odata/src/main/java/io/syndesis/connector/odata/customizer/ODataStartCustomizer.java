/*
 * Copyright (C) 2016 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.syndesis.connector.odata.customizer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.olingo.client.api.domain.ClientEntity;
import org.apache.olingo.client.api.domain.ClientEntitySet;
import org.apache.olingo.client.api.domain.ClientItem;
import org.apache.olingo.client.api.domain.ClientValue;
import org.apache.olingo.client.core.domain.ClientPrimitiveValueImpl;
import org.apache.olingo.client.core.domain.ClientPropertyImpl;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.syndesis.connector.odata.ODataConstants;
import io.syndesis.connector.odata.customizer.json.ClientCollectionValueSerializer;
import io.syndesis.connector.odata.customizer.json.ClientComplexValueSerializer;
import io.syndesis.connector.odata.customizer.json.ClientEntitySerializer;
import io.syndesis.connector.odata.customizer.json.ClientEnumValueSerializer;
import io.syndesis.connector.odata.customizer.json.ClientPrimitiveValueSerializer;
import io.syndesis.connector.odata.customizer.json.ClientPropertySerializer;
import io.syndesis.integration.component.proxy.ComponentProxyComponent;
import io.syndesis.integration.component.proxy.ComponentProxyCustomizer;

public class ODataStartCustomizer implements ComponentProxyCustomizer, CamelContextAware, ODataConstants {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        SimpleModule module =
            new SimpleModule(ClientEntitySet.class.getSimpleName(), new Version(1, 0, 0, null, null, null));
        module
            .addSerializer(new ClientEntitySerializer())
            .addSerializer(new ClientPropertySerializer())
            .addSerializer(new ClientPrimitiveValueSerializer())
            .addSerializer(new ClientEnumValueSerializer())
            .addSerializer(new ClientCollectionValueSerializer())
            .addSerializer(new ClientComplexValueSerializer(OBJECT_MAPPER));
        OBJECT_MAPPER.registerModule(module);
    }

    private CamelContext camelContext;

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return this.camelContext;
    }

    @Override
    public void customize(ComponentProxyComponent component, Map<String, Object> options) {
        component.setBeforeConsumer(this::beforeConsumer);
    }

    private void beforeConsumer(Exchange exchange) throws IOException {
        Message in = exchange.getIn();
        if (in.getBody(ClientItem.class) == null) {
            in.setBody(Collections.emptyList());
            return;
        }

        List<String> resultList = new ArrayList<>();
        ClientItem item = in.getBody(ClientItem.class);
        if (item instanceof ClientEntitySet) {
            //
            // If the results have not been split and returned as a
            // ClientEntitySet then split it up into a recognisable list
            //
            ClientEntitySet entitySet = (ClientEntitySet) item;
            List<ClientEntity> entities = entitySet.getEntities();
            for (ClientEntity entity : entities) {
                if (entitySet.getCount() != null) {
                    //
                    // If $count was set to true in the query then
                    // need to include the count in the entities
                    //
                    ClientValue value = new ClientPrimitiveValueImpl.BuilderImpl()
                                                .buildInt32(entitySet.getCount());
                    entity.getProperties().add(new ClientPropertyImpl(RESULT_COUNT, value));
                }

                resultList.add(OBJECT_MAPPER.writeValueAsString(entity));
            }
        }
        else {
            resultList.add(OBJECT_MAPPER.writeValueAsString(item));
        }

        in.setBody(resultList);
    }
}
