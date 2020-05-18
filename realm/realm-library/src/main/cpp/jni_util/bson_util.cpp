/*
 * Copyright 2020 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "io_realm_internal_jni_JniBsonProtocol.h"

#include <string>
#include <android/log.h>
#include "util.hpp"
#include "bson_util.hpp"

// Must match JniBsonProtocol.VALUE from Java
static const std::string VALUE("value");

using namespace realm::bson;
using namespace realm::jni_util;

Bson JniBsonProtocol::string_to_bson(std::string arg) {
    BsonDocument document(parse(arg));
    return document[VALUE];
}

Bson JniBsonProtocol::jstring_to_bson(JNIEnv* env, jstring arg) {
    return string_to_bson(JStringAccessor(env, arg));
}

std::string JniBsonProtocol::bson_to_string(Bson bson) {
    BsonDocument document{{VALUE, bson}};
    std::stringstream buffer;
    buffer << document;
    return buffer.str();
}

jstring JniBsonProtocol::bson_to_jstring(JNIEnv* env, Bson bson) {
    std::string r = bson_to_string(bson);
    return to_jstring(env, r);
};


// FIXME Do not include in release builds
// FIXME Better output tagging, use AndroidLogger class, etc.
JNIEXPORT jstring JNICALL
Java_io_realm_internal_jni_JniBsonProtocol_nativeRoundtrip(JNIEnv* env, jclass, jstring jinput) {
    try {
        JStringAccessor cinput(env, jinput);
        __android_log_print(ANDROID_LOG_DEBUG, "REALM", "nativeTest input: %s", std::string(cinput).c_str());
        
        Bson input = JniBsonProtocol::string_to_bson(cinput);

        std::stringstream buffer;
        buffer << input;
        __android_log_print(ANDROID_LOG_DEBUG, "REALM", "nativeTest parsed bson: %s", buffer.str().c_str());

        jstring joutput = JniBsonProtocol::bson_to_jstring(env, input);
        JStringAccessor output(env, joutput);
        __android_log_print(ANDROID_LOG_DEBUG, "REALM", "nativeTest output: %s", std::string(output).c_str());

        return joutput;
    }
    CATCH_STD()
    return NULL;
}
