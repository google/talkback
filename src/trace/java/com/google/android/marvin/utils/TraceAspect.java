/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.marvin.utils;

import android.util.Log;

import com.google.common.base.Strings;
import com.google.common.base.Stopwatch;
import com.googlecode.eyesfree.utils.LogUtils;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;


/**
 * Define the pointcuts of talkback application and default tracing behavior
 */
@Aspect
@SuppressWarnings("unused")
public class TraceAspect {

    // Define the point cut for methods with annotation of {@code DebugTrace}
    private static final String POINTCUT_METHOD =
            "execution(@com.google.android.marvin.utils.DebugTrace * *(..))";

    // Define the point cut for constructor with annotation of {@code DebugTrace}
    private static final String POINTCUT_CONSTRUCTOR =
            "execution(@com.google.android.marvin.utils.DebugTrace *.new(..))";

    // Define the point cut the under the package of talkback
    private static final String POINTCUT_TALKBACK_METHOD =
            "execution(* com.google.android.marvin.talkback..*.*(..))";

    @Pointcut(POINTCUT_METHOD)
    public void methodAnnotatedWithDebugTrace() {}

    @Pointcut(POINTCUT_CONSTRUCTOR)
    public void constructorAnnotatedDebugTrace() {}

    @Pointcut(POINTCUT_TALKBACK_METHOD)
    public void talkbackAllMethods() {}


    // Only generate logs for methods that takes longer than the value in ms
    private static final int LATENCY_THRESHOLD_MS = 10;

    // It control indentation of the log output to make it more readable
    private static int callLevel = 0;


    @Around("methodAnnotatedWithDebugTrace() " +
            "|| talkbackAllMethods()  " +
            "||  constructorAnnotatedDebugTrace()")
    public Object weaveJoinPoint(ProceedingJoinPoint joinPoint) throws Throwable {
        final Stopwatch stopWatch = new Stopwatch();
        stopWatch.start();
        callLevel++;
        Object result = joinPoint.proceed();
        stopWatch.stop();
        log(joinPoint, stopWatch.elapsedMillis());
        callLevel--;
        return result;
    }

    private void log(ProceedingJoinPoint joinPoint, long elapsedMillis) {
        if (elapsedMillis >= LATENCY_THRESHOLD_MS) {
            MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
            String className = methodSignature.getMethod().getDeclaringClass().getCanonicalName();
            String methodName = methodSignature.getName();
            LogUtils.log(className, Log.DEBUG,
                    "%s %s.%s -->%dms",
                    Strings.repeat("  ", callLevel > 0 ? callLevel : 0),
                    className,
                    methodName,
                    elapsedMillis);
        }
    }
}