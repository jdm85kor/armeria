/*
 * Copyright 2015 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
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
package com.linecorp.armeria.server;

import static java.util.Objects.requireNonNull;

import java.util.function.Function;
import java.util.regex.Pattern;

import com.linecorp.armeria.common.ServiceInvocationContext;
import com.linecorp.armeria.server.PathManipulators.Prepend;
import com.linecorp.armeria.server.PathManipulators.StripParents;
import com.linecorp.armeria.server.PathManipulators.StripPrefixByNumPathComponents;
import com.linecorp.armeria.server.PathManipulators.StripPrefixByPathPrefix;

/**
 * Matches the absolute path part of a URI and translates the matched path to another path string.
 * The translated path, returned by {@link #apply(String)}, determines the value of
 * {@link ServiceInvocationContext#mappedPath()}.
 */
@FunctionalInterface
public interface PathMapping extends Function<String, String> {

    /**
     * Creates a new {@link PathMapping} that matches a {@linkplain ServiceInvocationContext#path() path} with
     * the specified regular expression, as defined in {@link Pattern}. The returned {@link PathMapping} does
     * not perform any translation. To create a {@link PathMapping} that performs a translation, use the
     * decorator methods like {@link #stripPrefix(String)}.
     */
    static PathMapping ofRegex(Pattern regex) {
        return new RegexPathMapping(regex);
    }

    /**
     * Creates a new {@link PathMapping} that matches a {@linkplain ServiceInvocationContext#path() path} with
     * the specified glob expression, where {@code "*"} matches a path component non-recursively and
     * {@code "**"} matches path components recursively. The returned {@link PathMapping} does not perform any
     * translation. To create a {@link PathMapping} that performs a translation, use the decorator methods like
     * {@link #stripPrefix(String)}.
     */
    static PathMapping ofGlob(String glob) {
        requireNonNull(glob, "glob");
        if (glob.startsWith("/") && !glob.contains("*")) {
            // Does not have a pattern matcher.
            return ofExact(glob);
        }

        return new GlobPathMapping(glob);
    }

    /**
     * Creates a new {@link PathMapping} that matches a {@linkplain ServiceInvocationContext#path() path}
     * under the specified directory prefix. It also removes the specified directory prefix from the matched
     * path so that {@link ServiceInvocationContext#mappedPath()} does not have the specified directory prefix.
     * For example, when {@code pathPrefix} is {@code "/foo/"}:
     * <ul>
     *   <li>{@code "/foo/"} translates to {@code "/"}</li>
     *   <li>{@code "/foo/bar"} translates to  {@code "/bar"}</li>
     *   <li>{@code "/foo/bar/baz"} translates to {@code "/bar/baz"}</li>
     * </ul>
     * This method is a shortcut to {@link #ofPrefix(String, boolean) ofPrefix(pathPrefix, true)}.
     */
    static PathMapping ofPrefix(String pathPrefix) {
        return ofPrefix(pathPrefix, true);
    }

    /**
     * Creates a new {@link PathMapping} that matches a {@linkplain ServiceInvocationContext#path() path}
     * under the specified directory prefix. When {@code stripPrefix} is {@code true}, it also removes the
     * specified directory prefix from the matched path so that {@link ServiceInvocationContext#mappedPath()}
     * does not have the specified directory prefix. For example, when {@code pathPrefix} is {@code "/foo/"}:
     * <ul>
     *   <li>{@code "/foo/"} translates to {@code "/"}</li>
     *   <li>{@code "/foo/bar"} translates to  {@code "/bar"}</li>
     *   <li>{@code "/foo/bar/baz"} translates to {@code "/bar/baz"}</li>
     * </ul>
     */
    static PathMapping ofPrefix(String pathPrefix, boolean stripPrefix) {
        requireNonNull(pathPrefix, "pathPrefix");
        if ("/".equals(pathPrefix)) {
            // Every path starts with '/'.
            return ofCatchAll();
        }

        return new PrefixPathMapping(pathPrefix, stripPrefix);
    }

    /**
     * Creates a new {@link PathMapping} that performs an exact match. The returned {@link PathMapping} always
     * translates the matched path to {@code "/"}.
     */
    static PathMapping ofExact(String exactPath) {
        return new ExactPathMapping(exactPath);
    }

    /**
     * Returns a singleton {@link PathMapping} that always matches any path. The returned {@link PathMapping}
     * does not perform any translation. To create a {@link PathMapping} that performs a translation, use the
     * decorator methods like {@link #stripPrefix(String)}.
     */
    static PathMapping ofCatchAll() {
        return CatchAllPathMapping.INSTANCE;
    }

    /**
     * Matches the specified {@code path} and translates the matched {@code path} to another path string.
     *
     * @param path an absolute path as defined in <a href="https://tools.ietf.org/html/rfc3986">RFC3986</a>
     * @return the translated path which is used as the value of {@link ServiceInvocationContext#mappedPath()}.
     *         {@code null} if the specified {@code path} does not match this mapping.
     */
    @Override
    String apply(String path);

    /**
     * Creates a new {@link PathMapping} that removes the specified {@code pathPrefix} from the matched path
     * so that the {@link ServiceInvocationContext#mappedPath()} does not have the specified {@code pathPrefix}.
     * This method is useful when used with {@link #ofRegex(Pattern)} or {@link #ofGlob(String)}. For example:
     * <pre>{@code
     * PathMapping.ofRegex("^/foo/[^/]+/bar/.*$").stripPrefix("/foo/");
     * PathMapping.ofGlob("^/foo/&#42;/bar/&#42;&#42;").stripPrefix("/foo/");
     * }</pre>
     */
    default PathMapping stripPrefix(String pathPrefix) {
        requireNonNull(pathPrefix, "pathPrefix");
        if (pathPrefix.isEmpty()) {
            return this;
        }

        return new StripPrefixByPathPrefix(this, pathPrefix);
    }

    /**
     * Creates a new {@link PathMapping} that removes the first {@code <numPathComponents>} path components
     * from the matches path so that the {@link ServiceInvocationContext#mappedPath()} does not have
     * unnecessary path prefixes. This method is useful when used with {@link #ofRegex(Pattern)} or
     * {@link #ofGlob(String)}. For example:
     * <pre>{@code
     * PathMapping.ofRegex("^/(foo|bar)/[^/]+/baz/.*$").stripPrefix(1);
     * PathMapping.ofGlob("^/foo/&#42;/baz/&#42;&#42;").stripPrefix(1);
     * }</pre>
     */
    default PathMapping stripPrefix(int numPathComponents) {
        if (numPathComponents == 0) {
            return this;
        }

        return new StripPrefixByNumPathComponents(this, numPathComponents);
    }

    /**
     * Creates a new {@link PathMapping} that removes all parent components from the matched path so that
     * the {@link ServiceInvocationContext#mappedPath()} contains only a single path component. This method
     * is useful when you are interested only in the file name part of the path.
     */
    default PathMapping stripParents() {
        return new StripParents(this);
    }

    /**
     * Creates a new {@link PathMapping} that prepends the specified {@code pathPrefix} to the matched path.
     */
    default PathMapping prepend(String pathPrefix) {
        requireNonNull(pathPrefix, "pathPrefix");
        if (pathPrefix.isEmpty() || "/".equals(pathPrefix)) {
            return this;
        }

        return new Prepend(this, pathPrefix);
    }
}
