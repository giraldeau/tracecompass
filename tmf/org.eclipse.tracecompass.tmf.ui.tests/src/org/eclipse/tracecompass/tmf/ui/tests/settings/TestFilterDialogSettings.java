/*******************************************************************************
 * Copyright (c) 2013, 2014 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.tmf.ui.tests.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.tracecompass.common.core.NonNullUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;

/**
 * Test that the dialog settings for filtering
 *
 * @author Francis Giraldeau <francis.giraldeau@gmail.com>
 *
 */
@SuppressWarnings("javadoc")
public class TestFilterDialogSettings {

    // dummy class hierarchy
    public static class Foo {
        public final int fFoo;

        Foo(int foo) {
            fFoo = foo;
        }
    }

    public static class Bar extends Foo {
        public final int fBar;

        Bar(int foo, int bar) {
            super(foo);
            fBar = bar;
        }
    }

    public static class Baz extends Bar {
        public final int fBaz;

        Baz(int foo, int bar, int baz) {
            super(foo, bar);
            fBaz = baz;
        }
    }

    private static final String EXAMPLE_KEY = "test";
    private static final List<Foo> fItemsAll = Lists.newArrayList(new Foo(1), new Bar(2, 3), new Baz(4, 5, 6), new Foo(7));
    private static final List<Foo> fItemsFiltered = Lists.newArrayList(fItemsAll.subList(0, 2));
    private static final Function<Object, HashCode> fFooStableHash = new Function<Object, HashCode>() {
        @Override
        public HashCode apply(Object input) {
            return Hashing.sha1().newHasher().putInt(((Foo) input).fFoo).hash();
        }
    };
    private final List<Boolean> fWasCalled = new ArrayList<>();
    private final Set<Foo> fActualSet = new HashSet<>();
    private ViewerFilterProperties fViewerProp;
    private File fPropFile;

    /*
     * TODO: use heterogeneous typesafe container for the stable hash function
     * yuck: the generics types are not reifiable for Function<T, HashCode>,
     * meaning we need to rethink the typing of the class
     */
    public static class ViewerFilterProperties {

        private final File fPropertiesFile;
        private final StableHasherMap fHasherMap = new StableHasherMap();

        private static Runnable EMPTY_RUNNABLE = new Runnable() {
            @Override
            public void run() {
            }
        };

        // that doesn't do much... Should implements Converter<A, B> when
        // upgrading to Guava 16
        public static class Base64Converter {
            public static String convert(byte[] array) {
                return convert(ByteBuffer.wrap(array));
            }

            public static String convert(ByteBuffer buf) {
                return BaseEncoding.base64().encode(buf.array());
            }

            public static ByteBuffer reverse(String str) {
                return ByteBuffer.wrap(BaseEncoding.base64().decode(str));
            }
        }

        ViewerFilterProperties(File file) {
            fPropertiesFile = file;
        }

        /*
         * Callable is used because it throws Exception. It allows to forward
         * any error to the user with IStatus.
         */
        private static Job launch(Callable<?> function, Runnable callback) {
            Job job = new Job("save ViewerFilterProperties") {
                @Override
                protected IStatus run(IProgressMonitor monitor) {
                    try {
                        function.call();
                    } catch (Exception e) {
                        return new Status(IStatus.ERROR, "plug-in id", "message", e);
                    } finally {
                        callback.run();
                    }
                    return Status.OK_STATUS;
                }
            };
            job.schedule();
            return job;
        }

        public <T> Job save(Class<?> type, String key, Collection<? super T> filteredItems) {
            return save(type, key, filteredItems, EMPTY_RUNNABLE);
        }

        public <T> Job load(Class<?> type, String key, Collection<? extends T> allItems, Collection<? super T> filteredItems) {
            return load(type, key, allItems, filteredItems, EMPTY_RUNNABLE);
        }

        public <T> Job save(Class<?> type, String key, Collection<? super T> filteredItems, Runnable callback) {
            return launch(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    saveRaw(type, key, ImmutableList.copyOf(NonNullUtils.checkNotNull(filteredItems)));
                    return null;
                }
            }, callback);
        }

        public <T> Job load(Class<?> type, String key, Collection<? extends T> allItems, Collection<? super T> filteredItems, Runnable callback) {
            return launch(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    loadRaw(type, key, ImmutableList.copyOf(NonNullUtils.checkNotNull(allItems)), filteredItems);
                    return null;
                }
            }, callback);
        }

        public synchronized <T> void saveRaw(Class<?> type, String key, Collection<? super T> filteredItems) throws Exception {
            // Generate the byte stream
            Function<Object, HashCode> function = fHasherMap.get(type);
            if (function == null) {
                throw new RuntimeException();
            }
            ByteArrayOutputStream dataOut = new ByteArrayOutputStream();
            try (ObjectOutput out = new ObjectOutputStream(dataOut)) {
                out.writeInt(filteredItems.size());
                for (Object item : filteredItems) {
                    HashCode hash = function.apply(item);
                    out.writeObject(hash);
                }
            }

            // encode the byte stream to string
            String val = Base64Converter.convert(dataOut.toByteArray());

            // load the previous properties if any
            Properties prop = new Properties();

            // override any prior value
            prop.put(key, val);

            // write the whole settings
            try (OutputStream outStream = new BufferedOutputStream(
                    new FileOutputStream(fPropertiesFile))) {
                prop.store(outStream, "ViewerFilterSettings"); //$NON-NLS-1$
            }
        }

        // FIXME: what is the proper way to wrap such a long line?
        private synchronized <T> void loadRaw(Class<?> type, String key, Collection<? extends T> allItems, Collection<? super T> filteredItems) throws Exception {
            // load the previous properties
            filteredItems.clear();
            // FIXME: function can be null if the type is not registered
            Function<Object, HashCode> function = fHasherMap.get(type);
            Properties prop = loadProperties(new Properties(), fPropertiesFile);
            String value = prop.getProperty(key);
            if (value == null) {
                filteredItems.clear();
                return;
            }
            byte[] buf = Base64Converter.reverse(value).array();
            ByteArrayInputStream dataInput = new ByteArrayInputStream(buf);
            try (ObjectInput inputData = new ObjectInputStream(dataInput)) {
                // read the number of hashes then the hashes themselves
                int size = inputData.readInt();
                Set<HashCode> hashCodes = new HashSet<>();
                for (int i = 0; i < size; i++) {
                    Object obj = inputData.readObject();
                    if (obj instanceof HashCode) {
                        hashCodes.add((HashCode) obj);
                    }
                }
                // copy references corresponding to saved hash code
                for (T item : allItems) {
                    if (hashCodes.contains(function.apply(item))) {
                        filteredItems.add(item);
                    }
                }
            }
        }

        private static Properties loadProperties(Properties prop, File propFile) throws FileNotFoundException, IOException {
            if (propFile.canRead()) {
                try (InputStream inStream = new BufferedInputStream(
                        new FileInputStream(propFile))) {
                    prop.load(inStream);
                }
            }
            return prop;
        }

        public StableHasherMap getHasherMap() {
            return fHasherMap;
        }

    }

    private static void assertSetsEquals(Collection<?> set1, Collection<?> set2) {
        assertEquals(Sets.symmetricDifference(Sets.newHashSet(set1),
                Sets.newHashSet(set2)), Collections.EMPTY_SET);
    }

    @Before
    public void setup() throws IOException {
        fPropFile = File.createTempFile("test", "properties");
        fViewerProp = new ViewerFilterProperties(fPropFile);
        fViewerProp.getHasherMap().put(Foo.class, fFooStableHash);
        fActualSet.clear();
        fWasCalled.clear();
    }

    @After
    public void teardown() {
        fPropFile.delete();
    }

    @Test
    public void testRawViewerFilterProperties() throws Exception {
        fViewerProp.loadRaw(Foo.class, EXAMPLE_KEY, fItemsAll, fActualSet);
        assertTrue(fActualSet.isEmpty());
        fViewerProp.saveRaw(Foo.class, EXAMPLE_KEY, fItemsFiltered);
        fViewerProp.loadRaw(Foo.class, EXAMPLE_KEY, fItemsAll, fActualSet);
        assertSetsEquals(fItemsFiltered, fActualSet);
    }

    @Test
    public void testJobViewerFilterProperties() throws Exception {
        fViewerProp.saveRaw(Foo.class, EXAMPLE_KEY, fActualSet);
        Job j0 = fViewerProp.load(Foo.class, EXAMPLE_KEY, fItemsAll, fActualSet);
        j0.join();
        assertTrue(fActualSet.isEmpty());
        Job j1 = fViewerProp.save(Foo.class, EXAMPLE_KEY, fItemsFiltered);
        j1.join();
        Job j2 = fViewerProp.load(Foo.class, EXAMPLE_KEY, fItemsAll, fActualSet);
        j2.join();
        assertSetsEquals(fItemsFiltered, fActualSet);
    }

    @Test
    public void testCallbackViewerFilterProperties() throws Exception {
        Job j3 = fViewerProp.load(Foo.class, EXAMPLE_KEY, fItemsAll, fActualSet, () -> {
            synchronized (fWasCalled) {
                fWasCalled.add(Boolean.TRUE);
            }
        });
        Job j4 = fViewerProp.save(Foo.class, EXAMPLE_KEY, fItemsFiltered, () -> {
            synchronized (fWasCalled) {
                fWasCalled.add(Boolean.TRUE);
            }
        });
        j3.join();
        j4.join();
        assertEquals(2, fWasCalled.size());
        assertTrue(fPropFile.canRead());
        assertTrue(fPropFile.length() > 0);
    }

    public static class StableHasherMap {
        Map<Class<?>, Function<Object, HashCode>> map = new HashMap<>();

        public <T> void put(Class<T> type, Function<Object, HashCode> function) {
            map.put(type, function);
        }

        public <T> Function<Object, HashCode> get(Class<T> type) {
            return map.get(type);
        }

    }

    @Test
    public void testTypeToken() {
        StableHasherMap map = new StableHasherMap();
        map.put(Foo.class, fFooStableHash);
        Function<Object, HashCode> function = map.get(Foo.class);
        function.apply(new Foo(1));
    }

}
