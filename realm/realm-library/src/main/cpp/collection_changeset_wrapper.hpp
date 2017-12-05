/*
 * Copyright 2017 Realm Inc.
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

#ifndef REALM_JNI_IMPL_COLLECTION_CHANGESET_WRAPPER_HPP
#define REALM_JNI_IMPL_COLLECTION_CHANGESET_WRAPPER_HPP

#include "jni_util/java_class.hpp"
#include "jni_util/java_global_weak_ref.hpp"
#include "jni_util/java_method.hpp"
#include "jni_util/log.hpp"
#include "object-store/src/collection_notifications.hpp"
#include <collection_notifications.hpp>

namespace realm {
namespace _impl {

// Wrapper of Object Store CollectionChangeSet
// It is used to better control the mapping between Object Store concepts and Java API's, especially
// when it comes to states and defining errors.
class CollectionChangeSetWrapper {
public:
    CollectionChangeSetWrapper(CollectionChangeSet const& changeset, std::string error_message)
        : m_changeset(changeset)
        , m_error_message(error_message)
    {
    }

    ~CollectionChangeSetWrapper() = default;

    CollectionChangeSetWrapper(CollectionChangeSetWrapper&&) = delete;
    CollectionChangeSetWrapper& operator=(CollectionChangeSetWrapper&&) = delete;
    CollectionChangeSetWrapper(CollectionChangeSetWrapper const&) = delete;
    CollectionChangeSetWrapper& operator=(CollectionChangeSetWrapper const&) = delete;

    CollectionChangeSet& get()
    {
        return m_changeset;
    };

    jthrowable get_error() {
        if (m_error_message != nullptr) {
            return nullptr;
        } else if (m_changeset.partial_sync_error_message != nullptr) {
            return nullptr;
        } else {
            return nullptr;
        }
    }

    bool is_remote_data_loaded() {
        return m_changeset.partial_sync_new_status_code == 1;
    }


private:
    CollectionChangeSet m_changeset;
    std::string m_error_message; // From any exception being thrown that are not reported using Partial Sync
};


} // namespace realm
} // namespace _impl

#endif // REALM_JNI_IMPL_COLLECTION_CHANGESET_WRAPPER_HPP