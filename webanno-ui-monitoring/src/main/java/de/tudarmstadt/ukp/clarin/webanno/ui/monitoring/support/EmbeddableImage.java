/*
 * Copyright 2012
 * Ubiquitous Knowledge Processing (UKP) Lab and FG Language Technology
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.clarin.webanno.ui.monitoring.support;

import org.apache.wicket.markup.html.image.Image;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.request.resource.IResource;
import org.apache.wicket.request.resource.ResourceReference;

/**
 * A {@link Panel} to display embeddable immages inside a table.
 *
 *
 */
public class EmbeddableImage
    extends Panel
{
    private static final long serialVersionUID = -4541901391361133303L;

    public EmbeddableImage(String aComponentId, ResourceReference aResource)
    {
        super(aComponentId);
        add(new Image("image", aResource) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean shouldAddAntiCacheParameter()
            {
                return false;
            }
        });
    }

    public EmbeddableImage(String aComponentId, IResource aResource)
    {
        super(aComponentId);
        add(new Image("image", aResource) {
            private static final long serialVersionUID = 1L;
            
            @Override
            protected boolean shouldAddAntiCacheParameter()
            {
                return false;
            }
        });
    }
}
