/*
 * Copyright (c) 2016 Uber Technologies, Inc. (hoodie-dev-group@uber.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.uber.hoodie.common.util;

import com.uber.hoodie.common.model.HoodieRecord;

import com.uber.hoodie.exception.SchemaCompatabilityException;
import org.apache.avro.Schema;
import org.apache.avro.generic.*;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Helper class to do common stuff across Avro.
 */
public class HoodieAvroUtils {

    // All metadata fields are optional strings.
    private final static Schema METADATA_FIELD_SCHEMA = Schema.createUnion(Arrays.asList(
            Schema.create(Schema.Type.NULL),
            Schema.create(Schema.Type.STRING)));

    private final static Schema RECORD_KEY_SCHEMA = initRecordKeySchema();

    /**
     * Convert a given avro record to bytes
     */
    public static byte[] avroToBytes(GenericRecord record) throws IOException {
        GenericDatumWriter<GenericRecord> writer =
                new GenericDatumWriter<>(record.getSchema());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
        writer.write(record, encoder);
        encoder.flush();
        out.close();
        return out.toByteArray();
    }

    /**
     * Convert serialized bytes back into avro record
     */
    public static GenericRecord bytesToAvro(byte[] bytes, Schema schema) throws IOException {
        Decoder decoder = DecoderFactory.get().binaryDecoder(bytes, null);
        GenericDatumReader<GenericRecord> reader = new GenericDatumReader<GenericRecord>(schema);
        return reader.read(null, decoder);
    }


    /**
     * Adds the Hoodie metadata fields to the given schema
     */
    public static Schema addMetadataFields(Schema schema) {
        List<Schema.Field> parentFields = new ArrayList<>();

        Schema.Field commitTimeField = new Schema.Field(HoodieRecord.COMMIT_TIME_METADATA_FIELD, METADATA_FIELD_SCHEMA, "", null);
        Schema.Field commitSeqnoField = new Schema.Field(HoodieRecord.COMMIT_SEQNO_METADATA_FIELD, METADATA_FIELD_SCHEMA, "", null);
        Schema.Field recordKeyField = new Schema.Field(HoodieRecord.RECORD_KEY_METADATA_FIELD, METADATA_FIELD_SCHEMA, "", null);
        Schema.Field partitionPathField = new Schema.Field(HoodieRecord.PARTITION_PATH_METADATA_FIELD, METADATA_FIELD_SCHEMA, "", null);
        Schema.Field fileNameField = new Schema.Field(HoodieRecord.FILENAME_METADATA_FIELD, METADATA_FIELD_SCHEMA, "", null);

        parentFields.add(commitTimeField);
        parentFields.add(commitSeqnoField);
        parentFields.add(recordKeyField);
        parentFields.add(partitionPathField);
        parentFields.add(fileNameField);
        for (Schema.Field field : schema.getFields()) {
            parentFields.add(new Schema.Field(field.name(), field.schema(), field.doc(), null));
        }

        Schema mergedSchema = Schema.createRecord(schema.getName(), schema.getDoc(), schema.getNamespace(), false);
        mergedSchema.setFields(parentFields);
        return mergedSchema;
    }

    private static Schema initRecordKeySchema() {
        Schema.Field recordKeyField = new Schema.Field(HoodieRecord.RECORD_KEY_METADATA_FIELD, METADATA_FIELD_SCHEMA, "", null);
        Schema recordKeySchema = Schema.createRecord("HoodieRecordKey", "", "", false);
        recordKeySchema.setFields(Arrays.asList(recordKeyField));
        return recordKeySchema;
    }

    public static Schema getRecordKeySchema() {
        return RECORD_KEY_SCHEMA;
    }

    public static GenericRecord addHoodieKeyToRecord(GenericRecord record, String recordKey, String partitionPath, String fileName) {
        record.put(HoodieRecord.FILENAME_METADATA_FIELD, fileName);
        record.put(HoodieRecord.PARTITION_PATH_METADATA_FIELD, partitionPath);
        record.put(HoodieRecord.RECORD_KEY_METADATA_FIELD, recordKey);
        return record;
    }

    /**
     * Adds the Hoodie commit metadata into the provided Generic Record.
     */
    public static GenericRecord addCommitMetadataToRecord(GenericRecord record, String commitTime, String commitSeqno) {
        record.put(HoodieRecord.COMMIT_TIME_METADATA_FIELD, commitTime);
        record.put(HoodieRecord.COMMIT_SEQNO_METADATA_FIELD, commitSeqno);
        return record;
    }


    /**
     * Given a avro record with a given schema, rewrites it into the new schema
     */
    public static GenericRecord rewriteRecord(GenericRecord record, Schema newSchema) {
        GenericRecord newRecord = new GenericData.Record(newSchema);
        for (Schema.Field f : record.getSchema().getFields()) {
            newRecord.put(f.name(), record.get(f.name()));
        }
        if (!new GenericData().validate(newSchema, newRecord)) {
            throw new SchemaCompatabilityException(
                    "Unable to validate the rewritten record " + record + " against schema "
                            + newSchema);
        }
        return newRecord;
    }
}
