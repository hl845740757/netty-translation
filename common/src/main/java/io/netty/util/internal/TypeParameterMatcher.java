/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.util.internal;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashMap;
import java.util.Map;

/**
 * 泛型参数匹配器。
 * {@link TypeParameterMatcher}是非常重要的一个类，它对Netty的写出优雅的、强大的模板类起了重要作用。
 */
public abstract class TypeParameterMatcher {
    /**
     * 无操作的类型参数匹配器。
     * 总是返回匹配成功
     */
    private static final TypeParameterMatcher NOOP = new TypeParameterMatcher() {
        @Override
        public boolean match(Object msg) {
            return true;
        }
    };

    /**
     * 获取指定类型的匹配器
     * @param parameterType 被匹配的类型(是否可赋值给该类型)
     * @return
     */
    public static TypeParameterMatcher get(final Class<?> parameterType) {
        final Map<Class<?>, TypeParameterMatcher> getCache =
                InternalThreadLocalMap.get().typeParameterMatcherGetCache();

        TypeParameterMatcher matcher = getCache.get(parameterType);
        // 缓存中不存在，则创建，并放入缓存
        if (matcher == null) {
            // 如果Object，那么将匹配任何对象
            if (parameterType == Object.class) {
                matcher = NOOP;
            } else {
                // 创建反射类型的匹配器
                matcher = new ReflectiveMatcher(parameterType);
            }
            // 放入缓存
            getCache.put(parameterType, matcher);
        }

        return matcher;
    }

    /**
     * 获取对象的类型匹配器
     * @param object 实例对象
     * @param parametrizedSuperclass 对象的超类，必须是超类，Netty的实现中，不支持接口中查找
     * @param typeParamName parametrizedSuperclass中定义的泛型参数名字
     * @return
     */
    public static TypeParameterMatcher find(
            final Object object, final Class<?> parametrizedSuperclass, final String typeParamName) {

        final Map<Class<?>, Map<String, TypeParameterMatcher>> findCache =
                InternalThreadLocalMap.get().typeParameterMatcherFindCache();
        final Class<?> thisClass = object.getClass();

        Map<String, TypeParameterMatcher> map = findCache.get(thisClass);
        if (map == null) {
            map = new HashMap<String, TypeParameterMatcher>();
            findCache.put(thisClass, map);
        }

        TypeParameterMatcher matcher = map.get(typeParamName);
        if (matcher == null) {
            matcher = get(find0(object, parametrizedSuperclass, typeParamName));
            map.put(typeParamName, matcher);
        }

        return matcher;
    }

    /**
     * 获取对象的类型匹配器的真正实现。
     * 该方法很重要，理解其原理有必要，才能正确使用它和扩展它。
     * 在模板方法中，可以极大地优化代码。
     * @param object 实例对象
     * @param parametrizedSuperclass 对象的超类，必须是超类，Netty的实现中，不支持接口中查找
     * @param typeParamName parametrizedSuperclass中定义的泛型参数名字
     * @return
     */
    private static Class<?> find0(
            final Object object, Class<?> parametrizedSuperclass, String typeParamName) {

        final Class<?> thisClass = object.getClass();
        Class<?> currentClass = thisClass;
        for (;;) {
            // 1.找到了目标类(因为需要GenericSuperClass才能获取泛型的信息，因此需要递归寻找)
            if (currentClass.getSuperclass() == parametrizedSuperclass) {
                // 泛型参数在类中的索引
                int typeParamIndex = -1;
                TypeVariable<?>[] typeParams = currentClass.getSuperclass().getTypeParameters();
                for (int i = 0; i < typeParams.length; i ++) {
                    if (typeParamName.equals(typeParams[i].getName())) {
                        typeParamIndex = i;
                        break;
                    }
                }
                // 2.该泛型参数在类中不存在
                if (typeParamIndex < 0) {
                    throw new IllegalStateException(
                            "unknown type parameter '" + typeParamName + "': " + parametrizedSuperclass);
                }

                // Generic表示的是泛型类型，getGenericSuperclass()表示获取泛型超类
                // 同样的还有 currentClass.getGenericSuperclass(),获取所有的直接实现的泛型接口
                Type genericSuperType = currentClass.getGenericSuperclass();

                // Type顶层接口，有四种类型。Class,GenericArrayType,TypeVariable,WildcardType
                // 其中只有ParameterizedType可以获取真实泛型参数类型。
                // 3.如果不是ParameterizedType类型，通常表示着当前子类继承父类时抹去了父类的泛型。
                if (!(genericSuperType instanceof ParameterizedType)) {
                    return Object.class;
                }

                Type[] actualTypeParams = ((ParameterizedType) genericSuperType).getActualTypeArguments();
                // 获取对应索引的真实类型
                Type actualTypeParam = actualTypeParams[typeParamIndex];
                if (actualTypeParam instanceof ParameterizedType) {
                    // 4.仍然是个泛型类型，则获取其原始类型
                    actualTypeParam = ((ParameterizedType) actualTypeParam).getRawType();
                }

                // 5.返回点：如果真实类型是class，表示已找到，或真实类型是泛型类型，返回它的原始类型。
                if (actualTypeParam instanceof Class) {
                    return (Class<?>) actualTypeParam;
                }

                // 6.返回点：如果真实类型泛型数组，则获取数组元素的真实类型，并创建它的一个数组返回
                if (actualTypeParam instanceof GenericArrayType) {
                    Type componentType = ((GenericArrayType) actualTypeParam).getGenericComponentType();
                    if (componentType instanceof ParameterizedType) {
                        componentType = ((ParameterizedType) componentType).getRawType();
                    }
                    if (componentType instanceof Class) {
                        return Array.newInstance((Class<?>) componentType, 0).getClass();
                    }
                }

                // 7.如果真实类型是一个泛型变量，它通常意味着，当前子类在继承时使用了新的泛型参数(不论名字是否相同),会导致这里是泛型变量。
                // eg:  class A<E>{}  class B<E> extend A<E>{}
                if (actualTypeParam instanceof TypeVariable) {
                    // Resolved type parameter points to another type parameter.
                    TypeVariable<?> v = (TypeVariable<?>) actualTypeParam;
                    currentClass = thisClass;
                    // 8.如果声明新泛型变量的不是Class类型(类/接口)，则无法查找，返回Object.class
                    if (!(v.getGenericDeclaration() instanceof Class)) {
                        return Object.class;
                    }
                    // 9.另一个Class类型重新声明了该泛型参数，要查找的超类和泛型变量名都需要纠正。
                    parametrizedSuperclass = (Class<?>) v.getGenericDeclaration();
                    typeParamName = v.getName();

                    if (parametrizedSuperclass.isAssignableFrom(thisClass)) {
                        // 10.重新声明该泛型的类仍然是传入类的超类，则重新查找(查找被重新定义的泛型参数的真实类型)。！！！
                        continue;
                    } else {
                        // 11.返回点：声明该泛型参数的不是它的超类，无法继续查找，则返回object
                        return Object.class;
                    }
                }

                // 查找失败，没炸到目标类型，也无法继续查找
                return fail(thisClass, typeParamName);
            }
            currentClass = currentClass.getSuperclass();
            if (currentClass == null) {
                return fail(thisClass, typeParamName);
            }
        }
    }

    /**
     * 进行失败处理，抛出异常
     * @param type
     * @param typeParamName
     * @return
     */
    private static Class<?> fail(Class<?> type, String typeParamName) {
        throw new IllegalStateException(
                "cannot determine the type of the type parameter '" + typeParamName + "': " + type);
    }

    /**
     * 查询给定的对象是否匹配
     * @param msg
     * @return
     */
    public abstract boolean match(Object msg);

    /**
     * 基于反射的类型匹配器
     */
    private static final class ReflectiveMatcher extends TypeParameterMatcher {
        private final Class<?> type;

        ReflectiveMatcher(Class<?> type) {
            this.type = type;
        }

        @Override
        public boolean match(Object msg) {
            // 等价于 msg instanceOf Type
            return type.isInstance(msg);
        }
    }

    TypeParameterMatcher() { }
}
