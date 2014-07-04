/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.ribbon.examples.rx;

import com.netflix.hystrix.util.HystrixTimer;
import com.netflix.ribbon.examples.rx.common.Movie;
import io.netty.buffer.ByteBuf;
import rx.Notification;
import rx.Observable;
import rx.functions.Func1;
import rx.functions.Func2;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static java.lang.String.*;

/**
 * Base class for the transport/template and proxy examples. It orchestrates application flow, and
 * handles result(s) from the server. The request execution is implemented in the derived classes, in a way
 * specific for each API abstration. This separation of concerns makes it is easy to compare complexity of different APIs.
 *
 * @author Tomasz Bak
 */
public abstract class AbstractRxMovieClient {
    protected static final String TEST_USER = "user1";
    protected static final Pattern NEW_LINE_SPLIT_RE = Pattern.compile("\n");

    protected abstract Observable<Void>[] triggerMoviesRegistration();

    protected abstract Observable<Void>[] triggerRecommendationsUpdate();

    protected abstract Observable<ByteBuf>[] triggerRecommendationsSearch();

    protected boolean registerMovies() {
        System.out.print("Registering movies...");

        Notification<Void> status = Observable.concat(Observable.from(triggerMoviesRegistration())).materialize().toBlocking().last();

        if (status.isOnError()) {
            System.err.println("ERROR");
            status.getThrowable().printStackTrace();
            return false;
        }
        System.out.println("DONE");
        return true;
    }

    protected boolean updateRecommendations() {
        System.out.print("Updating user recommendations...");

        Notification<Void> status = Observable.concat(Observable.from(triggerRecommendationsUpdate())).materialize().toBlocking().last();

        if (status.isOnError()) {
            System.err.println("ERROR");
            status.getThrowable().printStackTrace();
            return false;
        }
        System.out.println("DONE");
        return true;
    }

    protected boolean searchCatalog() {
        System.out.println("Searching through the movie catalog...");

        List<String> searches = new ArrayList<String>(2);
        Collections.addAll(searches, "findById", "findRawMovieById", "findMovie(name, category)");

        Notification<Void> status = Observable.concat(Observable.from(triggerRecommendationsSearch())).flatMap(new Func1<ByteBuf, Observable<List<Movie>>>() {
            @Override
            public Observable<List<Movie>> call(ByteBuf byteBuf) {
                List<Movie> movies = new ArrayList<Movie>();
                String lines = byteBuf.toString(Charset.defaultCharset());
                for (String line : NEW_LINE_SPLIT_RE.split(lines)) {
                    movies.add(Movie.from(line));
                }
                return Observable.just(movies);
            }
        }).zip(searches, new Func2<List<Movie>, String, Void>() {
            @Override
            public Void call(List<Movie> movies, String query) {
                System.out.println(format("    %s=%s", query, movies));
                return null;
            }
        }).materialize().toBlocking().last();

        if (status.isOnError()) {
            System.err.println("ERROR");
            status.getThrowable().printStackTrace();
            return false;
        }
        System.out.println("DONE");
        return true;
    }

    public void execute() {
        try {
            if (registerMovies() && updateRecommendations() && searchCatalog()) {
                System.out.println("Application finished");
            } else {
                System.err.println("ERROR: execution failure");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            shutdown();
        }
    }

    protected void shutdown() {
        HystrixTimer.reset();
    }
}
