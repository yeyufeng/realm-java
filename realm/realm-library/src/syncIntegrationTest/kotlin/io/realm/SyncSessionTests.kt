package io.realm

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.realm.entities.*
import io.realm.exceptions.DownloadingRealmInterruptedException
import io.realm.internal.OsRealmConfig
import io.realm.kotlin.syncSession
import io.realm.log.LogLevel
import io.realm.log.RealmLog
import io.realm.rule.BlockingLooperThread
import io.realm.util.ResourceContainer
import io.realm.util.assertFailsWithMessage
import org.bson.BsonInt32
import org.bson.BsonInt64
import org.bson.BsonObjectId
import org.bson.BsonString
import org.bson.types.ObjectId
import org.hamcrest.CoreMatchers
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.Closeable
import java.lang.Thread
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

typealias SessionCallback = (SyncSession) -> Unit

private val SECRET_PASSWORD = "123456"

@RunWith(AndroidJUnit4::class)
class SyncSessionTests {

    @get:Rule
    private val looperThread = BlockingLooperThread()

    private lateinit var app: TestRealmApp
    private lateinit var user: RealmUser
    private lateinit var syncConfiguration: SyncConfiguration

    private val configFactory: TestSyncConfigurationFactory = TestSyncConfigurationFactory()

    private fun getSession(callback: SessionCallback) {
        // Work-around for a race condition happening when shutting down a Looper test and
        // Resetting the SyncManager
        // The problem is the `@After` block which runs as soon as the test method has completed.
        // For integration tests this will attempt to reset the SyncManager which will fail
        // if Realms are still open as they hold a reference to a session object.
        // By moving this into a Looper callback we ensure that a looper test can shutdown as
        // intended.
        // Generally it seems that using calling `RunInLooperThread.testComplete()` in a synchronous
        looperThread.postRunnable(Runnable {
            val user = app.registerUserAndLogin(TestHelper.getRandomEmail(), SECRET_PASSWORD)
            val syncConfiguration = configFactory
                    .createSyncConfigurationBuilder(user)
                    .build()
            val realm = Realm.getInstance(syncConfiguration)
            looperThread.closeAfterTest(realm)
            callback(realm.syncSession)
        })
    }

    private fun getActiveSession(callback: SessionCallback) {
        getSession { session ->
            if (session.isConnected) {
                callback(session)
            } else {
                session.addConnectionChangeListener(object : ConnectionListener {
                    override fun onChange(oldState: ConnectionState, newState: ConnectionState) {
                        if (newState == ConnectionState.CONNECTED) {
                            session.removeConnectionChangeListener(this)
                            callback(session)
                        }
                    }
                })
            }
        }
    }

    @Before
    fun setup() {
        Realm.init(InstrumentationRegistry.getInstrumentation().targetContext)
        RealmLog.setLevel(LogLevel.ALL)
        app = TestRealmApp()
        user = app.registerUserAndLogin(TestHelper.getRandomEmail(), SECRET_PASSWORD)
        syncConfiguration = configFactory
                .createSyncConfigurationBuilder(user)
                .schema(AllTypes::class.java, Dog::class.java, Owner::class.java, Cat::class.java, DogPrimaryKey::class.java)
                .build()
    }

    @After
    fun teardown() {
        if (this::app.isInitialized) {
            app.close()
        }
        RealmLog.setLevel(LogLevel.WARN)
    }

    @Test
    // FIXME Investigate further
    @Ignore("Works on first run, but generates Bad changeset on subsequent runs. Even after " +
            "just running one of the other partitionValue test...even if registering a new user")
    fun partitionValue_string() {
        // FIXME See comment for partitionValue_int32
        val partitionValue = "123464652"
        val syncConfiguration = configFactory
                .createSyncConfigurationBuilder(user, BsonString(partitionValue))
                .schema(AllTypes::class.java, Dog::class.java, Owner::class.java, Cat::class.java, DogPrimaryKey::class.java)
                .build()
        Realm.getInstance(syncConfiguration).use { realm ->
            realm.executeTransaction {
                realm.createObject(AllTypes::class.java, ObjectId())
            }
            realm.syncSession.uploadAllLocalChanges()
        }
    }

    @Test
    // FIXME Investigate further
    @Ignore("Works on first run, but generates Bad changeset on subsequent runs. Even after " +
            "just running one of the other partitionValue test...even if registering a new user")
    fun partitionValue_int32() {
        // FIXME Seems like we cannot repeatedly connect if we change the partitionValue, subsequent
        //  runs will fail with a
        //    Connection[1]: Session[1]: Failed to transform received changeset: Schema mismatch: Property 'columnStringList' in class 'AllTypes' is nullable on one side and not on the other.
        //    Connection[1]: Connection closed due to error
        //    Session Error[ws://127.0.0.1:9090/]: CLIENT_BAD_CHANGESET(realm::sync::Client::Error:112): Bad changeset (DOWNLOAD)
        val int = 123536462
        val syncConfiguration = configFactory
                .createSyncConfigurationBuilder(user, BsonInt32(int))
                .schema(AllTypes::class.java, Dog::class.java, Owner::class.java, Cat::class.java, DogPrimaryKey::class.java)
                .build()
        Realm.getInstance(syncConfiguration).use { realm ->
            realm.executeTransaction {
                realm.createObject(AllTypes::class.java, ObjectId())
            }
            realm.syncSession.uploadAllLocalChanges()
        }
    }

    @Test
    // FIXME Investigate further
    @Ignore("Works on first run, but generates Bad changeset on subsequent runs. Even after " +
            "just running one of the other partitionValue test...even if registering a new user")
    fun partitionValue_int64() {
        // FIXME See comment for partitionValue_int32
        val long = 1243513244L
        val syncConfiguration = configFactory
                .createSyncConfigurationBuilder(user, BsonInt64(long))
                .schema(AllTypes::class.java, Dog::class.java, Owner::class.java, Cat::class.java, DogPrimaryKey::class.java)
                .build()
        Realm.getInstance(syncConfiguration).use { realm ->
            realm.executeTransaction {
                realm.createObject(AllTypes::class.java, ObjectId())
            }
            realm.syncSession.uploadAllLocalChanges()
        }
    }

    @Test
    // FIXME Investigate further
    @Ignore("Works on first run, but generates Bad changeset on subsequent runs. Even after " +
            "just running one of the other partitionValue test...even if registering a new user")
    fun partitionValue_objectId() {
        // FIXME See comment for partitionValue_int32
        val objectId = ObjectId("5ecf72df02aa3c32ab6b4ce0")
        val syncConfiguration = configFactory
                .createSyncConfigurationBuilder(user, BsonObjectId(objectId))
                .schema(AllTypes::class.java, Dog::class.java, Owner::class.java, Cat::class.java, DogPrimaryKey::class.java)
                .build()
        Realm.getInstance(syncConfiguration).use { realm ->
            realm.executeTransaction {
                realm.createObject(AllTypes::class.java, ObjectId())
            }
            realm.syncSession.uploadAllLocalChanges()
        }
    }

    @Test
    // FIXME Differentiate path for Realms with different partition values
    @Ignore("Partition value does not generate different paths")
    fun differentPathsForDifferentPartitionValues() {
        val syncConfiguration1 = configFactory
                .createSyncConfigurationBuilder(user, BsonString("partitionvalue1"))
                .schema(AllTypes::class.java, Dog::class.java, Owner::class.java, Cat::class.java, DogPrimaryKey::class.java)
                .build()
        val syncConfiguration2 = configFactory
                .createSyncConfigurationBuilder(user, BsonString("partitionvalue2"))
                .schema(AllTypes::class.java, Dog::class.java, Owner::class.java, Cat::class.java, DogPrimaryKey::class.java)
                .build()
        Realm.getInstance(syncConfiguration1).use { realm1 ->
            Realm.getInstance(syncConfiguration2).use { realm2 ->
                assertNotEquals(realm1, realm2)
                assertNotEquals(realm1.path, realm2.path)
            }
        }
    }

    @Test(timeout = 3000)
    fun getState_active() {
        Realm.getInstance(syncConfiguration).use { realm ->
            val session: SyncSession = realm.syncSession

            // make sure the `access_token` is acquired. otherwise we can still be
            // in WAITING_FOR_ACCESS_TOKEN state
            while (session.state != SyncSession.State.ACTIVE) {
                SystemClock.sleep(200)
            }
        }
    }

    @Test
    fun getState_throwOnClosedSession() {
        var session: SyncSession? = null
        Realm.getInstance(syncConfiguration).use { realm ->
            session = realm.syncSession
        }
        user.logOut()

        assertFailsWithMessage<java.lang.IllegalStateException>(CoreMatchers.equalTo("Could not find session, Realm was probably closed")) {
            session!!.state
        }
    }

    @Test
    fun getState_loggedOut() {
        Realm.getInstance(syncConfiguration).use { realm ->
            val session = realm.syncSession
            user.logOut();
            assertEquals(SyncSession.State.INACTIVE, session.state);
        }
    }

    @Test
    // FIXME Find a way to flush data from server between each run
    @Ignore("Needs clean server as asserting on number of rows of a specific class")
    fun uploadDownloadAllChangesWorking() {
        Realm.getInstance(syncConfiguration).use { realm ->
            realm.executeTransaction {
                realm.createObject(AllTypes::class.java, ObjectId())
            }
            realm.syncSession.uploadAllLocalChanges()
        }

        // New user but same Realm as configuration has the same partition value
        val user2 = app.registerUserAndLogin(TestHelper.getRandomEmail(), SECRET_PASSWORD)
        val config2 = configFactory
                .createSyncConfigurationBuilder(user2)
                .build()

        Realm.getInstance(config2).use { realm ->
            realm.syncSession.downloadAllServerChanges()
            realm.refresh()
            // FIXME Requires server to flush data between each run
            assertEquals(1, realm.where(AllTypes::class.java).count())
        }
    }

    @Test
    // FIXME Find a way to flush data from server between each run
    @Ignore("Needs clean server as asserting on number of rows of a specific class")
    fun uploadDownloadAllChanges() {
        Realm.getInstance(syncConfiguration).use { realm ->
            realm.executeTransaction {
                realm.createObject(AllTypes::class.java, ObjectId())
            }
            realm.syncSession.uploadAllLocalChanges()
        }

        // New user but same Realm as configuration has the same partition value
        val user2 = app.registerUserAndLogin(TestHelper.getRandomEmail(), SECRET_PASSWORD)
        val config2 = configFactory
                .createSyncConfigurationBuilder(user2, syncConfiguration.partitionValue)
                .build()

        Realm.getInstance(config2).use { realm ->
            realm.syncSession.downloadAllServerChanges()
            realm.refresh()
            // FIXME Requires server to flush data between each run
            assertEquals(1, realm.where(AllTypes::class.java).count())
        }
    }

    @Test
    // FIXME Find a way to flush data from server between each run
    @Ignore("Needs clean server as asserting on number of rows of a specific class")
    fun interruptWaits() {
        // FIXME Convert to BackgroundLooperThread? Is it doable with all the interruptions
        val t = Thread(Runnable {
            Realm.getInstance(syncConfiguration).use { userRealm ->
                userRealm.executeTransaction {
                    userRealm.createObject(AllTypes::class.java, ObjectId())
                }
                val userSession = userRealm.syncSession
                try {
                    // 1. Start download (which will be interrupted)
                    Thread.currentThread().interrupt()
                    userSession.downloadAllServerChanges()
                    fail()
                } catch (ignored: InterruptedException) {
                    assertFalse(Thread.currentThread().isInterrupted)
                }
                try {
                    // 2. Upload all changes
                    userSession.uploadAllLocalChanges()
                } catch (e: InterruptedException) {
                    fail("Upload interrupted")
                }
            }

            // New user but same Realm as configuration has the same partition value
            val user2 = app.registerUserAndLogin(TestHelper.getRandomEmail(), SECRET_PASSWORD)
            val config2 = configFactory
                    .createSyncConfigurationBuilder(user2)
                    .build()

            Realm.getInstance(config2).use { adminRealm ->
                val adminSession: SyncSession = adminRealm.syncSession
                try {
                    // 3. Start upload (which will be interrupted)
                    Thread.currentThread().interrupt()
                    adminSession.uploadAllLocalChanges()
                    fail()
                } catch (ignored: InterruptedException) {
                    assertFalse(Thread.currentThread().isInterrupted) // clear interrupted flag
                }
                try {
                    // 4. Download all changes
                    adminSession.downloadAllServerChanges()
                } catch (e: InterruptedException) {
                    fail("Download interrupted")
                }
                adminRealm.refresh()

                // FIXME Requires server to flush data
                assertEquals(1, adminRealm.where(AllTypes::class.java).count())
            }
        })
        t.start()
        t.join()
    }

    // check that logging out a SyncUser used by different Realm will
    // affect all associated sessions.
    @Test(timeout = 5000)
    // FIXME Differentiate path for Realms with different partition values, see differentPathsForDifferentPartitionValues
    @Ignore("Partition value does not generate different paths")
    fun logout_sameSyncUserMultipleSessions() {
        Realm.getInstance(syncConfiguration).use { realm1 ->
            // New partitionValue to differentiate sync session
            val syncConfiguration2 = configFactory
                    .createSyncConfigurationBuilder(user, BsonObjectId(ObjectId()))
                    .schema(AllTypes::class.java, Dog::class.java, Owner::class.java, Cat::class.java, DogPrimaryKey::class.java)
                    .build()

            Realm.getInstance(syncConfiguration2).use { realm2 ->
                val session1: SyncSession = realm1.syncSession
                val session2: SyncSession = realm2.syncSession

                // make sure the `access_token` is acquired. otherwise we can still be
                // in WAITING_FOR_ACCESS_TOKEN state
                // FIXME Reavaluate with new sync states
                while (session1.state != SyncSession.State.ACTIVE || session2.state != SyncSession.State.ACTIVE) {
                    SystemClock.sleep(200)
                }

                assertEquals(SyncSession.State.ACTIVE, session1.state)
                assertEquals(SyncSession.State.ACTIVE, session2.state)
                assertNotEquals(realm1, realm2)
                assertNotEquals(session1, session2)
                assertEquals(session1.user, session2.user)
                user.logOut()
                assertEquals(SyncSession.State.INACTIVE, session1.state)
                assertEquals(SyncSession.State.INACTIVE, session2.state)

                // Login again
                app.login(RealmCredentials.emailPassword(user.email!!, SECRET_PASSWORD))

                // reviving the sessions. The state could be changed concurrently.
                // FIXME Reavaluate with new sync states
                assertTrue(
                        //session1.state == SyncSession.State.WAITING_FOR_ACCESS_TOKEN ||
                        session1.state == SyncSession.State.ACTIVE)
                assertTrue(
                        //session2.state == SyncSession.State.WAITING_FOR_ACCESS_TOKEN ||
                        session2.state == SyncSession.State.ACTIVE)
            }
        }
    }

    // A Realm that was opened before a user logged out should be able to resume uploading if the user logs back in.
    @Test
    // FIXME Investigate further
    // FIXME Rewrite to use BlockingLooperThread
    @Ignore("Re-logging in does not authorize")
    fun logBackResumeUpload() {
        val config1 = configFactory
                .createSyncConfigurationBuilder(user)
                .modules(StringOnlyModule())
                .waitForInitialRemoteData()
                .build()
        Realm.getInstance(config1).use { realm1 ->
            realm1.executeTransaction { realm -> realm.createObject(StringOnly::class.java, ObjectId()).chars = "1" }
            val session1: SyncSession = realm1.syncSession
            session1.uploadAllLocalChanges()
            user.logOut()

            // add a commit while we're still offline
            realm1.executeTransaction { realm -> realm.createObject(StringOnly::class.java, ObjectId()).chars = "2" }
            val testCompleted = CountDownLatch(1)
            val handlerThread = HandlerThread("HandlerThread")
            handlerThread.start()
            val looper = handlerThread.looper
            val handler = Handler(looper)
            val allResults = AtomicReference<RealmResults<StringOnly>>() // notifier could be GC'ed before it get a chance to trigger the second commit, so declaring it outside the Runnable
            handler.post { // access the Realm from an different path on the device (using admin user), then monitor
                // when the offline commits get synchronized
                // FIXME Do we somehow need to extract the refreshtoken...and could it be the reason for app.login not working later on
                val user2 = app.registerUserAndLogin(TestHelper.getRandomEmail(), SECRET_PASSWORD)
                val config2: SyncConfiguration = configFactory.createSyncConfigurationBuilder(user2, config1.partitionValue)
                        .modules(StringOnlyModule())
                        .waitForInitialRemoteData()
                        .build()
                val realm2 = Realm.getInstance(config2)

                allResults.set(realm2.where(StringOnly::class.java).sort(StringOnly.FIELD_CHARS).findAll())
                val realmChangeListener: RealmChangeListener<RealmResults<StringOnly>> = object : RealmChangeListener<RealmResults<StringOnly>> {
                    override fun onChange(stringOnlies: RealmResults<StringOnly>) {
                        if (stringOnlies.size == 2) {
                            assertEquals("1", stringOnlies[0]!!.chars)
                            assertEquals("2", stringOnlies[1]!!.chars)
                            handler.post {

                                // Closing a Realm from inside a listener doesn't seem to remove the
                                // active session reference in Object Store
                                realm2.close()
                                testCompleted.countDown()
                                handlerThread.quitSafely()
                            }
                        }
                    }
                }
                allResults.get().addChangeListener(realmChangeListener)

                // login again to re-activate the user
                val credentials = RealmCredentials.emailPassword(user.email!!, SECRET_PASSWORD)
                // this login will re-activate the logged out user, and resume all it's pending sessions
                // the OS will trigger bindSessionWithConfig with the new refresh_token, in order to obtain
                // a new access_token.
                app.login(credentials)
            }
            TestHelper.awaitOrFail(testCompleted)
        }
    }

    // A Realm that was opened before a user logged out should be able to resume uploading if the user logs back in.
    // this test validate the behaviour of SyncSessionStopPolicy::AfterChangesUploaded
    @Test
    // FIXME Investigate why it does not terminate...probably rewrite to BlockingLooperThread
    @Ignore("Does not terminate")
    fun uploadChangesWhenRealmOutOfScope() {
        val strongRefs: MutableList<Any> = ArrayList()
        val chars = CharArray(1000000) // 2MB
        Arrays.fill(chars, '.')
        val twoMBString = String(chars)
        val config1 = configFactory
                .createSyncConfigurationBuilder(user)
                .sessionStopPolicy(OsRealmConfig.SyncSessionStopPolicy.AFTER_CHANGES_UPLOADED)
                .modules(StringOnlyModule())
                .build()
        Realm.getInstance(config1).use { realm ->
            realm.executeTransaction {
                // upload 10MB
                for (i in 0..4) {
                    realm.createObject(StringOnly::class.java, ObjectId()).chars = twoMBString
                }
            }
        }

        val testCompleted = CountDownLatch(1)
        val handlerThread = HandlerThread("HandlerThread")
        handlerThread.start()
        val looper = handlerThread.looper
        val handler = Handler(looper)
        handler.post { // using an other user to open the Realm on different path on the device to monitor when all the uploads are done
            val user2 = app.registerUserAndLogin(TestHelper.getRandomEmail(), SECRET_PASSWORD)
            val config2: SyncConfiguration = configFactory.createSyncConfigurationBuilder(user2, config1.partitionValue)
                    .modules(StringOnlyModule())
                    .build()
            Realm.getInstance(config2).use { realm2 ->
                val all = realm2.where(StringOnly::class.java).findAll()
                if (all.size == 5) {
                    realm2.close()
                    testCompleted.countDown()
                    handlerThread.quit()
                } else {
                    strongRefs.add(all)
                    val realmChangeListener = OrderedRealmCollectionChangeListener { results: RealmResults<StringOnly?>, changeSet: OrderedCollectionChangeSet? ->
                        if (results.size == 5) {
                            realm2.close()
                            testCompleted.countDown()
                            handlerThread.quit()
                        }
                    }
                    all.addChangeListener(realmChangeListener)
                }
            }
            handlerThread.quit()
        }
        TestHelper.awaitOrFail(testCompleted, TestHelper.STANDARD_WAIT_SECS)
        handlerThread.join()
        user.logOut()
    }

    // A Realm that was opened before a user logged out should be able to resume downloading if the user logs back in.
    @Test
    // FIXME Investigate why it does not terminate...probably rewrite to BlockingLooperThread
    @Ignore("Does not terminate")
    fun downloadChangesWhenRealmOutOfScope() {
        val uniqueName = UUID.randomUUID().toString()
        var credentials = SyncCredentials.usernamePassword(uniqueName, "password", true)
        val config1 = configFactory
                .createSyncConfigurationBuilder(user)
                .modules(StringOnlyModule())
                .build()
        Realm.getInstance(config1).use { realm ->
            realm.executeTransaction {
                realm.createObject(StringOnly::class.java, ObjectId()).chars = "1"
            }
            val session: SyncSession = realm.syncSession
            session.uploadAllLocalChanges()

            // Log out the user.
            user.logOut()

            // Log the user back in.
            val credentials = RealmCredentials.emailPassword(user.email!!, SECRET_PASSWORD)
            app.login(credentials)

            // now let the admin upload some commits
            val backgroundUpload = CountDownLatch(1)
            val handlerThread = HandlerThread("HandlerThread")
            handlerThread.start()
            val looper = handlerThread.looper
            val handler = Handler(looper)
            handler.post { // using an admin user to open the Realm on different path on the device then some commits
                val user2 = app.registerUserAndLogin(TestHelper.getRandomEmail(), SECRET_PASSWORD)
                val config2: SyncConfiguration = configFactory.createSyncConfigurationBuilder(user2, config1.partitionValue)
                        .modules(StringOnlyModule())
                        .waitForInitialRemoteData()
                        .build()
                Realm.getInstance(config2).use { realm2 ->
                    realm2.executeTransaction {
                        realm2.createObject(StringOnly::class.java, ObjectId()).chars = "2"
                        realm2.createObject(StringOnly::class.java, ObjectId()).chars = "3"
                    }
                    realm2.syncSession.uploadAllLocalChanges()
                }
                backgroundUpload.countDown()
                handlerThread.quit()
            }
            TestHelper.awaitOrFail(backgroundUpload, 60)
            // Resume downloading
            session.downloadAllServerChanges()
            realm.refresh() //FIXME not calling refresh will still point to the previous version of the Realm count == 1
            assertEquals(3, realm.where(StringOnly::class.java).count())
        }
    }

    // Check that if we manually trigger a Client Reset, then it should be possible to start
    // downloading the Realm immediately after.
    @Test
    // TODO Seems to align with tests in SessionTests, should we move them to same location
    fun clientReset_manualTriggerAllowSessionToRestart() = looperThread.runBlocking {
        val resources = ResourceContainer()

        val configRef = AtomicReference<SyncConfiguration?>(null)
        val config: SyncConfiguration = configFactory.createSyncConfigurationBuilder(user)
                .clientResyncMode(ClientResyncMode.MANUAL)
                // FIXME Is this critical for the test
                // .directory(looperThread.getRoot())
                .errorHandler { session, error ->
                    val handler = error as ClientResetRequiredError
                    // Execute Client Reset
                    resources.close()
                    handler.executeClientReset()

                    // Try to re-open Realm and download it again
                    looperThread.postRunnable(Runnable { // Validate that files have been moved
                        assertFalse(handler.originalFile.exists())
                        assertTrue(handler.backupFile.exists())
                        val config = configRef.get()
                        Realm.getInstance(config!!).use { realm ->
                            realm.syncSession.downloadAllServerChanges()
                            looperThread.testComplete()
                        }
                    })
                }
                .build()
        configRef.set(config)
        val realm = Realm.getInstance(config)
        resources.add(realm)
        // Trigger error
        user.app.sync.simulateClientReset(realm.syncSession)
    }

    @Test
    // FIXME Implement connection listeners
    @Ignore("Connection listener callback is not implemented yet")
    fun registerConnectionListener() = looperThread.runBlocking {
        getSession { session: SyncSession ->
            session.addConnectionChangeListener { oldState: ConnectionState?, newState: ConnectionState ->
                if (newState == ConnectionState.DISCONNECTED) {
                    // Closing a Realm inside a connection listener doesn't work: https://github.com/realm/realm-java/issues/6249
                    looperThread.postRunnable(Runnable { looperThread.testComplete() })
                }
            }
            session.stop()
        }
    }

    @Test
    // FIXME Implement connection listeners
    @Ignore("Connection listener callback is not implemented yet")
    fun removeConnectionListener() = looperThread.runBlocking {
        Realm.getInstance(syncConfiguration).use { realm ->
            val session: SyncSession = realm.syncSession
            val listener1 = ConnectionListener { oldState: ConnectionState?, newState: ConnectionState ->
                if (newState == ConnectionState.DISCONNECTED) {
                    fail("Listener should have been removed")
                }
            }
            var listener2 = object : ConnectionListener {
                override fun onChange(oldState: ConnectionState, newState: ConnectionState) {
                    if (newState == ConnectionState.DISCONNECTED) {
                        looperThread.testComplete()
                    }
                }
            }
            session.addConnectionChangeListener(listener1)
            session.addConnectionChangeListener(listener2)
            session.removeConnectionChangeListener(listener1)
        }
    }

    @Test
    // FIXME Implement connection listeners
    @Ignore("Connection listener callback is not implemented yet")
    fun getIsConnected() = looperThread.runBlocking {
        getActiveSession { session: SyncSession ->
            assertEquals(session.connectionState, ConnectionState.CONNECTED)
            assertTrue(session.isConnected)
            looperThread.testComplete()
        }
    }

    @Test
    // FIXME Implement connection listeners
    @Ignore("Connection listener callback is not implemented yet")
    fun stopStartSession() = looperThread.runBlocking {
        getActiveSession { session: SyncSession ->
            assertEquals(SyncSession.State.ACTIVE, session.state)
            session.stop()
            assertEquals(SyncSession.State.INACTIVE, session.state)
            session.start()
            assertNotEquals(SyncSession.State.INACTIVE, session.state)
            looperThread.testComplete()
        }
    }

    @Test
    // FIXME Implement connection listeners
    @Ignore("Connection listener callback is not implemented yet")
    fun start_multipleTimes() = looperThread.runBlocking {
        getActiveSession { session ->
            session.start()
            assertEquals(SyncSession.State.ACTIVE, session.state)
            session.start()
            assertEquals(SyncSession.State.ACTIVE, session.state)
            looperThread.testComplete()
        }
    }

    @Test
    // FIXME Implement connection listeners
    @Ignore("Connection listener callback is not implemented yet")
    fun stop_multipleTimes() = looperThread.runBlocking {
        getActiveSession { session ->
            session.stop()
            assertEquals(SyncSession.State.INACTIVE, session.state)
            session.stop()
            assertEquals(SyncSession.State.INACTIVE, session.state)
            looperThread.testComplete()
        }
    }

    @Test
    // FIXME Investigate
    @Ignore("Asserts with no_session when tearing down, meaning that all session are not " +
            "closed, but realm seems to be closed, so further investigation is needed")
    fun waitForInitialRemoteData_throwsOnTimeout() = looperThread.runBlocking {
        val syncConfiguration = configFactory
                .createSyncConfigurationBuilder(user)
                .schema(AllTypes::class.java, Dog::class.java, Owner::class.java, Cat::class.java, DogPrimaryKey::class.java)
                .initialData { bgRealm: Realm ->
                    for (i in 0..99) {
                        bgRealm.createObject(AllTypes::class.java, ObjectId())
                    }
                }
                .waitForInitialRemoteData(1, TimeUnit.MILLISECONDS)
                .build()
        assertFailsWith<DownloadingRealmInterruptedException> {
            val instance = Realm.getInstance(syncConfiguration)
            looperThread.closeAfterTest(Closeable {
                instance.syncSession.close()
                instance.close()
            })
        }
        looperThread.testComplete()
    }

}
