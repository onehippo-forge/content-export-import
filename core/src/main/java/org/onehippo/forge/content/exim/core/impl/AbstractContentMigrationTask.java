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
package org.onehippo.forge.content.exim.core.impl;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import javax.jcr.Value;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.vfs2.FileFilter;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSelector;
import org.apache.commons.vfs2.FileSystemException;
import org.onehippo.forge.content.exim.core.ContentMigrationException;
import org.onehippo.forge.content.exim.core.ContentMigrationRecord;
import org.onehippo.forge.content.exim.core.ContentMigrationTask;
import org.onehippo.forge.content.exim.core.DocumentManager;
import org.onehippo.forge.content.exim.core.util.FileFilterDepthSelector;
import org.onehippo.forge.content.exim.core.util.NamePatternFileFilter;
import org.onehippo.forge.content.pojo.common.ContentValueConverter;
import org.onehippo.forge.content.pojo.common.jcr.DefaultJcrContentValueConverter;
import org.onehippo.forge.content.pojo.model.ContentNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

abstract public class AbstractContentMigrationTask implements ContentMigrationTask {

    private Logger logger = LoggerFactory.getLogger(AbstractContentMigrationTask.class);

    private static ThreadLocal<ContentMigrationRecord> tlCurrentContentMigrationRecord = new ThreadLocal<>();

    private long startedTimeMillis;
    private long stoppedTimeMillis;
    private List<ContentMigrationRecord> contentMigrationRecords = new LinkedList<>();

    private final DocumentManager documentManager;
    private ObjectMapper objectMapper;
    private ContentValueConverter<Value> contentValueConverter;

    public AbstractContentMigrationTask(final DocumentManager documentManager) {
        this.documentManager = documentManager;
    }

    public Logger getLogger() {
        return logger;
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    public void start() {
        if (isStarted()) {
            throw new IllegalStateException("Task was already started.");
        }

        startedTimeMillis = System.currentTimeMillis();
        stoppedTimeMillis = 0L;
        contentMigrationRecords.clear();
        tlCurrentContentMigrationRecord.remove();
    }

    public void stop() {
        if (!isStarted()) {
            throw new IllegalStateException("Task was not started.");
        }

        stoppedTimeMillis = System.currentTimeMillis();
        tlCurrentContentMigrationRecord.remove();
    }

    public long getStartedTimeMillis() {
        return startedTimeMillis;
    }

    public long getStoppedTimeMillis() {
        return stoppedTimeMillis;
    }

    @Override
    public ContentMigrationRecord beginRecord(String contentId, String contentPath) {
        ContentMigrationRecord record = new ContentMigrationRecord();
        record.setContentId(contentId);
        record.setContentPath(contentPath);
        contentMigrationRecords.add(record);
        tlCurrentContentMigrationRecord.set(record);
        return record;
    }

    @Override
    public ContentMigrationRecord endRecord() {
        ContentMigrationRecord record = getCurrentContentMigrationRecord();
        tlCurrentContentMigrationRecord.remove();
        return record;
    }

    public Collection<ContentMigrationRecord> getContentMigrationRecords() {
        return Collections.unmodifiableCollection(contentMigrationRecords);
    }

    public static ContentMigrationRecord getCurrentContentMigrationRecord() {
        return tlCurrentContentMigrationRecord.get();
    }

    public void logSummary() {
        StringBuilder sb = new StringBuilder(1024);

        int totalCount = 0;
        int processedCount = 0;
        int successCount = 0;

        for (ContentMigrationRecord record : getContentMigrationRecords()) {
            ++totalCount;

            if (record.isProcessed()) {
                ++processedCount;

                if (record.isSucceeded()) {
                    ++successCount;
                }
            }
        }

        sb.append(
                "===============================================================================================================\n");
        sb.append("Execution Summary:\n");
        sb.append(
                "---------------------------------------------------------------------------------------------------------------\n");
        sb.append("Total: ").append(totalCount).append(", Processed: ").append(processedCount).append(", Succeeded: ")
                .append(successCount).append(", Failed: ").append(processedCount - successCount).append(", Duration: ")
                .append(getStoppedTimeMillis() - getStartedTimeMillis()).append("ms").append("\n");
        sb.append(
                "---------------------------------------------------------------------------------------------------------------\n");
        sb.append("Details (in CSV format):\n");
        sb.append(
                "---------------------------------------------------------------------------------------------------------------\n");
        sb.append("Processed,Succeeded,ID,Path,Type,Attributes,Error\n");

        for (ContentMigrationRecord record : getContentMigrationRecords()) {
            sb.append(record.isProcessed()).append(',').append(record.isSucceeded()).append(',')
            .append(StringUtils.defaultString(record.getContentId())).append(',')
                    .append(StringUtils.defaultString(record.getContentPath())).append(',')
                    .append(StringUtils.defaultString(record.getContentType())).append(',')
                    .append(record.getAttributeMap()).append(',')
                    .append(StringUtils.defaultString(record.getErrorMessage())).append('\n');
        }

        sb.append(
                "===============================================================================================================\n");

        getLogger().info("\n\n{}\n", sb);
    }

    public DocumentManager getDocumentManager() {
        return documentManager;
    }

    public ObjectMapper getObjectMapper() {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();
        }

        return objectMapper;
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ContentValueConverter<Value> getContentValueConverter() {
        if (contentValueConverter == null) {
            contentValueConverter = new DefaultJcrContentValueConverter(getDocumentManager().getSession());
        }

        return contentValueConverter;
    }

    public void setContentValueConverter(ContentValueConverter<Value> contentValueConverter) {
        this.contentValueConverter = contentValueConverter;
    }

    public FileObject[] findFilesByNamePattern(FileObject baseFolder, String nameRegex, int minDepth, int maxDepth)
            throws FileSystemException {
        final FileFilter fileFilter = new NamePatternFileFilter(Pattern.compile(nameRegex));
        final FileSelector selector = new FileFilterDepthSelector(fileFilter, minDepth, maxDepth);
        return baseFolder.findFiles(selector);
    }

    @Override
    public ContentNode readContentNodeFromJsonFile(final FileObject sourceFile) throws ContentMigrationException {
        ContentNode contentNode = null;

        InputStream is = null;
        BufferedInputStream bis = null;

        try {
            is = sourceFile.getContent().getInputStream();
            bis = new BufferedInputStream(is);
            contentNode = getObjectMapper().readValue(bis, ContentNode.class);
        } catch (IOException e) {
            throw new ContentMigrationException(e.toString(), e);
        } finally {
            IOUtils.closeQuietly(bis);
            IOUtils.closeQuietly(is);
        }

        return contentNode;
    }

    @Override
    public void writeContentNodeToJsonFile(final ContentNode contentNode, final FileObject targetFile) throws ContentMigrationException {
        OutputStream os = null;
        BufferedOutputStream bos = null;

        try {
            os = targetFile.getContent().getOutputStream();
            bos = new BufferedOutputStream(os);
            getObjectMapper().writerWithDefaultPrettyPrinter().writeValue(bos, contentNode);
        } catch (IOException e) {
            throw new ContentMigrationException(e.toString(), e);
        } finally {
            IOUtils.closeQuietly(bos);
            IOUtils.closeQuietly(os);
        }
    }

    private boolean isStarted() {
        return startedTimeMillis != 0;
    }

}