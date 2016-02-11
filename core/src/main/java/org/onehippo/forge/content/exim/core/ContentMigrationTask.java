/*
 * Copyright 2016-2016 Hippo B.V. (http://www.onehippo.com)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *         http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onehippo.forge.content.exim.core;

import java.util.Collection;

import org.apache.commons.vfs2.FileObject;
import org.onehippo.forge.content.pojo.model.ContentNode;
import org.slf4j.Logger;

public interface ContentMigrationTask {

    public Logger getLogger();

    public void setLogger(Logger logger);

    public void start();

    public void stop();

    public long getStartedTimeMillis();

    public long getStoppedTimeMillis();

    public ContentMigrationRecord beginRecord(String contentId, String contentPath);

    public ContentMigrationRecord endRecord();

    public Collection<ContentMigrationRecord> getContentMigrationRecords();

    public void logSummary();

    public ContentNode readContentNodeFromJsonFile(FileObject sourceFile) throws ContentMigrationException;

    public void writeContentNodeToJsonFile(ContentNode contentNode, FileObject targetFile)
            throws ContentMigrationException;

}