/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.performance.regression.android

import org.gradle.performance.AbstractCrossBuildPerformanceTest
import org.gradle.performance.fixture.BuildExperimentInvocationInfo
import org.gradle.performance.fixture.BuildExperimentListenerAdapter
import org.gradle.performance.fixture.GradleInvocationSpec
import org.gradle.performance.results.BaselineVersion
import org.gradle.performance.results.CrossBuildPerformanceResults
import org.gradle.util.GFileUtils
import spock.lang.Unroll

class RealLifeAndroidDexingTransformsPerformanceTest extends AbstractCrossBuildPerformanceTest {

    private static final String DEXING_TRANSFORM = "dexing transform"
    public static final String DEXING_TASK = "dexing task"

    @Unroll
    def "dexing task vs transform: #tasks on #testProject without (android/transform) cache"() {
        given:
        def invocationOptions = [tasks: tasks, memory: memory, enableAndroidBuildCache: false]

        runner.testGroup = "Android dexing"
        runner.buildSpec {
            projectName(testProject)
            displayName(DEXING_TRANSFORM)
            warmUpCount warmUpRuns
            invocationCount runs
            listener(cleanTransformsCache())
            invocation {
                defaultInvocation(*:invocationOptions, dexingTransforms: true, delegate)
            }
        }

        runner.baseline {
            projectName(testProject)
            displayName(DEXING_TASK)
            warmUpCount warmUpRuns
            invocationCount runs
            listener(cleanTransformsCache())
            invocation {
                defaultInvocation(*:invocationOptions, dexingTransforms: false, delegate)
            }
        }

        when:
        def results = runner.run()

        then:
        results.assertEveryBuildSucceeds()
        and:
        assertDexingTransformIsFaster(results)

        where:
        testProject         | memory | warmUpRuns | runs | tasks
        'largeAndroidBuild' | '5g'   | 2          | 4    | 'clean assembleDebug'
    }

    @Unroll
    def "dexing task vs transform: #tasks on #testProject"() {
        given:
        def dexingTransform = DEXING_TRANSFORM
        def dexingTask = DEXING_TASK

        def invocationOptions = [tasks: tasks, memory: memory, enableAndroidBuildCache: true]

        runner.testGroup = "Android dexing"
        runner.buildSpec {
            projectName(testProject)
            displayName(dexingTransform)
            warmUpCount warmUpRuns
            invocationCount runs
            invocation {
                defaultInvocation(*:invocationOptions, dexingTransforms: true, delegate)
            }
        }

        runner.baseline {
            projectName(testProject)
            displayName(dexingTask)
            warmUpCount warmUpRuns
            invocationCount runs
            invocation {
                defaultInvocation(*:invocationOptions, dexingTransforms: false, delegate)
            }
        }

        when:
        def results = runner.run()

        then:
        results.assertEveryBuildSucceeds()
        and:
        assertDexingTransformIsFaster(results)

        where:
        testProject         | memory | warmUpRuns | runs | tasks
        'largeAndroidBuild' | '5g'   | 2          | 4    | 'clean assembleDebug'
    }

    private static BuildExperimentListenerAdapter cleanTransformsCache() {
        new BuildExperimentListenerAdapter() {
            @Override
            void beforeInvocation(BuildExperimentInvocationInfo invocationInfo) {
                GFileUtils.deleteDirectory(new File(invocationInfo.gradleUserHome, "caches/transforms-1/files-1.1"))
            }
        }
    }

    void defaultInvocation(Map options, GradleInvocationSpec.InvocationBuilder builder) {
        String memory = options.memory
        String[] tasks = options.tasks.toString().split(' ')
        with(builder) {
            tasksToRun(tasks)
            cleanTasks("clean")
            gradleOpts("-Xms${memory}", "-Xmx${memory}")
            useDaemon()
            args("-Dorg.gradle.parallel=true", "-Pandroid.enableBuildCache=${options.enableAndroidBuildCache ?: true}", "-Pandroid.enableDexingArtifactTransform=${options.dexingTransforms}", '-Dcom.android.build.gradle.overrideVersionCheck=true')
        }

    }

    private static void assertDexingTransformIsFaster(CrossBuildPerformanceResults results) {
        def transformResults = results.buildResult(DEXING_TRANSFORM)
        BaselineVersion taskResults = dexingTaskResult(results)
        def speedStats = taskResults.getSpeedStatsAgainst(DEXING_TRANSFORM, transformResults)
        println(speedStats)
        if (taskResults.significantlyFasterThan(transformResults)) {
            throw new AssertionError(speedStats)
        }

    }

    private static BaselineVersion dexingTaskResult(CrossBuildPerformanceResults result) {
        def taskResults = new BaselineVersion("")
        taskResults.with {
            results.name = DEXING_TASK
            results.addAll(result.buildResult(DEXING_TASK))
        }
        taskResults
    }
}
