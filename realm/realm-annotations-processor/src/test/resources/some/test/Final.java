/*
 * Copyright 2014 Realm Inc.
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

package some.test;

import io.realm.RealmObject;

public class Final extends RealmObject {
    private final String name;
    private int age;

    public String getName() {
        return realmGetter$name();
    }

    public void setName(String name) {
        realmSetter$name(name);
    }

    public String realmGetter$name() {
        return name;
    }

    public void realmSetter$name(String name) {
        this.name = name;
    }

    public int getAge() {
        return realmGetter$age();
    }

    public void setAge(int age) {
        realmSetter$age(age);
    }

    public int realmGetter$age() {
        return age;
    }

    public void realmSetter$age(int age) {
        this.age = age;
    }

    @Override
    public String toString() {
        return "Simple{" +
                "name='" + name + '\'' +
                ", age=" + age +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Simple simple = (Simple) o;

        if (age != simple.age) return false;
        if (name != null ? !name.equals(simple.name) : simple.name != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + age;
        return result;
    }
}
