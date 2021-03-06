package com.moez.QKSMS.ui;

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import butterknife.Bind;
import butterknife.ButterKnife;
import com.google.android.mms.pdu_alt.PduHeaders;
import com.moez.QKSMS.R;
import com.moez.QKSMS.common.ConversationPrefsHelper;
import com.moez.QKSMS.common.DonationManager;
import com.moez.QKSMS.common.LiveViewManager;
import com.moez.QKSMS.common.QKRateSnack;
import com.moez.QKSMS.common.google.DraftCache;
import com.moez.QKSMS.common.utils.KeyboardUtils;
import com.moez.QKSMS.common.utils.MessageUtils;
import com.moez.QKSMS.common.utils.Units;
import com.moez.QKSMS.data.Conversation;
import com.moez.QKSMS.enums.QKPreference;
import com.moez.QKSMS.receiver.IconColorReceiver;
import com.moez.QKSMS.transaction.NotificationManager;
import com.moez.QKSMS.transaction.SmsHelper;
import com.moez.QKSMS.ui.base.QKActivity;
import com.moez.QKSMS.ui.compose.ComposeFragment;
import com.moez.QKSMS.ui.conversationlist.ConversationListFragment;
import com.moez.QKSMS.ui.dialog.ConversationSettingsDialog;
import com.moez.QKSMS.ui.dialog.DefaultSmsHelper;
import com.moez.QKSMS.ui.dialog.QKDialog;
import com.moez.QKSMS.ui.dialog.mms.MMSSetupFragment;
import com.moez.QKSMS.ui.messagelist.MessageListFragment;
import com.moez.QKSMS.ui.popup.QKReplyActivity;
import com.moez.QKSMS.ui.search.SearchActivity;
import com.moez.QKSMS.ui.settings.SettingsFragment;
import com.moez.QKSMS.ui.view.slidingmenu.SlidingMenu;
import com.moez.QKSMS.ui.welcome.WelcomeActivity;
import org.ligi.snackengage.SnackEngage;
import org.ligi.snackengage.snacks.BaseSnack;

import java.util.Collection;


public class MainActivity extends QKActivity implements SlidingMenu.SlidingMenuListener {
    private final String TAG = "MainActivity";

    public final static String EXTRA_THREAD_ID = "thread_id";

    public static long sThreadShowing;

    private static final int THREAD_LIST_QUERY_TOKEN = 1701;
    private static final int UNREAD_THREADS_QUERY_TOKEN = 1702;
    public static final int DELETE_CONVERSATION_TOKEN = 1801;
    public static final int HAVE_LOCKED_MESSAGES_TOKEN = 1802;
    private static final int DELETE_OBSOLETE_THREADS_TOKEN = 1803;

    public static final String MMS_SETUP_DONT_ASK_AGAIN = "mmsSetupDontAskAgain";

    @Bind(R.id.root) View mRoot;
    @Bind(R.id.sliding_menu) SlidingMenu mSlidingMenu;

    private ConversationListFragment mConversationList;
    private ContentFragment mContent;
    private long mWaitingForThreadId = -1;

    private boolean mIsDestroyed = false;

    /**
     * True if the mms setup fragment has been dismissed and we shouldn't show it anymore.
     */
    private final String KEY_MMS_SETUP_FRAGMENT_DISMISSED = "mmsSetupFragmentShown";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        launchWelcomeActivity();

        setContentView(R.layout.activity_main);
        setTitle(R.string.title_conversation_list);
        ButterKnife.bind(this);

        setSlidingTabEnabled(mPrefs.getBoolean(SettingsFragment.SLIDING_TAB, false));
        mSlidingMenu.setListener(this);
        mSlidingMenu.setContent();
        mSlidingMenu.setMenu();
        mSlidingMenu.showContent(false);
        mSlidingMenu.showMenu(false);

        FragmentManager fm = getFragmentManager();

        mConversationList = (ConversationListFragment) fm.findFragmentById(R.id.menu_frame);
        if (mConversationList == null) {
            mConversationList = new ConversationListFragment();
        }
        FragmentTransaction menuTransaction = fm.beginTransaction();
        menuTransaction.replace(R.id.menu_frame, mConversationList);
        menuTransaction.commit();

        mContent = (ContentFragment) fm.findFragmentById(R.id.content_frame);
        if (mContent == null) {
            mContent = ComposeFragment.getInstance(null);
        }
        FragmentTransaction contentTransaction = fm.beginTransaction();
        contentTransaction.replace(R.id.content_frame, (Fragment) mContent);
        contentTransaction.commit();

        onNewIntent(getIntent());
        showDialogIfNeeded(savedInstanceState);

        LiveViewManager.registerView(QKPreference.BACKGROUND, this, key -> {
            // Update the background color. This code is important during the welcome screen setup, when the activity
            // in the ThemeManager isn't the MainActivity
            mRoot.setBackgroundColor(ThemeManager.getBackgroundColor());
        });

        //Adds a small/non intrusive snackbar that asks the user to rate the app
        SnackEngage.from(this).withSnack(new QKRateSnack().withDuration(BaseSnack.DURATION_LONG))
                .build().engageWhenAppropriate();
    }

    /**
     * Shows at most one dialog using the intent extras and the restored state of the activity.
     *
     * @param savedInstanceState restored state
     */
    private void showDialogIfNeeded(Bundle savedInstanceState) {
        // Check if the intent has the ICON_COLOR_CHANGED action; if so, show a new dialog.
        if (getIntent().getBooleanExtra(IconColorReceiver.EXTRA_ICON_COLOR_CHANGED, false)) {
            // Clear the flag in the intent so that the dialog doesn't show up anymore
            getIntent().putExtra(IconColorReceiver.EXTRA_ICON_COLOR_CHANGED, false);

            // Display a dialog showcasing the new icon!
            ImageView imageView = new ImageView(this);
            PackageManager manager = getPackageManager();
            try {
                ComponentInfo info = manager.getActivityInfo(getComponentName(), 0);
                imageView.setImageDrawable(ContextCompat.getDrawable(getBaseContext(), info.getIconResource()));
            } catch (PackageManager.NameNotFoundException ignored) {
            }

            new QKDialog()
                    .setContext(this)
                    .setTitle(getString(R.string.icon_ready))
                    .setMessage(R.string.icon_ready_message)
                    .setCustomView(imageView)
                    .setPositiveButton(R.string.okay, null)
                    .show();

            // Only show the MMS setup fragment if it hasn't already been dismissed
        } else if (!wasMmsSetupFragmentDismissed(savedInstanceState)) {
            beginMmsSetup();
        }
    }

    private boolean wasMmsSetupFragmentDismissed(Bundle savedInstanceState) {
        // It hasn't been dismissed if the saved instance state isn't initialized, or is initialized
        // but doesn't have the flag.
        return savedInstanceState != null
                && savedInstanceState.getBoolean(KEY_MMS_SETUP_FRAGMENT_DISMISSED, false);
    }

    private void launchWelcomeActivity() {
        if (mPrefs.getBoolean(SettingsFragment.WELCOME_SEEN, false)) {
            // User has already seen the welcome screen
            return;
        }

        Intent welcomeIntent = new Intent(this, WelcomeActivity.class);
        startActivityForResult(welcomeIntent, WelcomeActivity.WELCOME_REQUEST_CODE);
    }

    public void showMenu() {
        mSlidingMenu.showMenu();
    }

    public SlidingMenu getSlidingMenu() {
        return mSlidingMenu;
    }

    /**
     * Configured the sliding menu view to peek the content or not.
     *
     * @param slidingTabEnabled true to peek the content
     */
    public void setSlidingTabEnabled(boolean slidingTabEnabled) {
        if (slidingTabEnabled) {
            mSlidingMenu.setBehindOffset(Units.dpToPx(this, 48));
        } else {
            mSlidingMenu.setBehindOffset(0);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        menu.clear();

        if (mSlidingMenu.isMenuShowing() || mContent == null) {
            showBackButton(false);
            mConversationList.inflateToolbar(menu, inflater, this);
        } else {
            showBackButton(true);
            mContent.inflateToolbar(menu, inflater, this);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    public Fragment getContent() {
        return (Fragment) mContent;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onKeyUp(KeyEvent.KEYCODE_BACK, null);
                return true;
            case R.id.menu_search:
                startActivity(SearchActivity.class);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void getResultForThreadId(long threadId) {
        mWaitingForThreadId = threadId;
    }

    /**
     * When In-App billing is done, it'll return information via onActivityResult().
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ConversationSettingsDialog.RINGTONE_REQUEST_CODE) {
            if (data != null) {
                if (mWaitingForThreadId > 0) {
                    ConversationPrefsHelper conversationPrefs = new ConversationPrefsHelper(this, mWaitingForThreadId);
                    Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                    conversationPrefs.putString(SettingsFragment.NOTIFICATION_TONE, uri.toString());
                    mWaitingForThreadId = -1;
                }
            }

        } else if (requestCode == WelcomeActivity.WELCOME_REQUEST_CODE) {
            new DefaultSmsHelper(this, R.string.not_default_first).showIfNotDefault(null);
        }
    }

    public static Intent createAddContactIntent(String address) {
        // address must be a single recipient
        Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
        intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
        if (SmsHelper.isEmailAddress(address)) {
            intent.putExtra(ContactsContract.Intents.Insert.EMAIL, address);
        } else {
            intent.putExtra(ContactsContract.Intents.Insert.PHONE, address);
            intent.putExtra(ContactsContract.Intents.Insert.PHONE_TYPE,
                    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);

        return intent;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mIsDestroyed = true;
        DonationManager.getInstance(this).destroy();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!mSlidingMenu.isMenuShowing()) {
                mSlidingMenu.showMenu();
                return true;
            } else {
                if (mConversationList.isShowingBlocked()) {
                    mConversationList.setShowingBlocked(false);
                } else {
                    finish();
                }
            }
        }

        return false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        sThreadShowing = 0;
        if (!mSlidingMenu.isMenuShowing()) {
            mContent.onContentClosed();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Only mark screen if the screen is on. onStart() is still called if the app is in the
        // foreground and the screen is off
        // TODO this solution doesn't work if the activity is in the foreground but the lockscreen is on
        if (isScreenOn()) {
            SmsHelper.markSmsSeen(this);
            SmsHelper.markMmsSeen(this);
            NotificationManager.update(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!mSlidingMenu.isMenuShowing()) {
            mContent.onContentOpened();
        }

        if (mContent != null && mContent instanceof MessageListFragment) {
            sThreadShowing = ((MessageListFragment) mContent).getThreadId();
            QKReplyActivity.dismiss(sThreadShowing);
        } else {
            sThreadShowing = 0;
        }

        NotificationManager.initQuickCompose(this, false, false);
    }

    private void beginMmsSetup() {
        if (!mPrefs.getBoolean(MMS_SETUP_DONT_ASK_AGAIN, false) &&
                TextUtils.isEmpty(mPrefs.getString(SettingsFragment.MMSC_URL, "")) &&
                TextUtils.isEmpty(mPrefs.getString(SettingsFragment.MMS_PROXY, "")) &&
                TextUtils.isEmpty(mPrefs.getString(SettingsFragment.MMS_PORT, ""))) {

            // Launch the MMS setup fragment here. This is a series of dialogs that will guide the
            // user through the MMS setup process.
            FragmentManager manager = getFragmentManager();
            if (manager.findFragmentByTag(MMSSetupFragment.TAG) == null) {
                MMSSetupFragment f = new MMSSetupFragment();
                Bundle args = new Bundle();
                args.putBoolean(MMSSetupFragment.ARG_ASK_FIRST, true);
                args.putString(MMSSetupFragment.ARG_DONT_ASK_AGAIN_PREF, MMS_SETUP_DONT_ASK_AGAIN);
                f.setArguments(args);

                getFragmentManager()
                        .beginTransaction()
                        .add(f, MMSSetupFragment.TAG)
                        .commit();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        FragmentManager m = getFragmentManager();

        // Save whether or not the mms setup fragment was dismissed
        if (m.findFragmentByTag(MMSSetupFragment.TAG) == null) {
            outState.putBoolean(KEY_MMS_SETUP_FRAGMENT_DISMISSED, true);
        }
    }

    public void switchContent(ContentFragment fragment, boolean animate) {
        // Make sure that the activity isn't destroyed before making fragment transactions.
        if (fragment != null && !mIsDestroyed) {
            KeyboardUtils.hide(this);

            mContent = fragment;
            FragmentManager m = getFragmentManager();

            // Only do a replace if it is a different fragment.
            if (fragment != m.findFragmentById(R.id.content_frame)) {
                getFragmentManager()
                        .beginTransaction()
                        .replace(R.id.content_frame, (Fragment) fragment)
                        .commitAllowingStateLoss();
            }

            mSlidingMenu.showContent(animate);
            invalidateOptionsMenu();

        } else {
            Log.w(TAG, "Null fragment, can't switch content");
        }
    }

    @Override
    public void onOpen() {
        invalidateOptionsMenu();
        sThreadShowing = 0;

        // Notify the content that it is being closed, since the menu (i.e. conversation list) is being opened.
        if (mContent != null) mContent.onContentClosing();

        // Hide the soft keyboard
        KeyboardUtils.hide(this, getCurrentFocus());

        showBackButton(false);
    }

    @Override
    public void onClose() {
        invalidateOptionsMenu();

        // Notify the content that it is being opened, since the menu (i.e. conversation list) is being closed.
        if (mContent != null) {
            mContent.onContentOpening();
        }

        if (mContent != null && mContent instanceof MessageListFragment) {
            sThreadShowing = ((MessageListFragment) mContent).getThreadId();
        } else {
            sThreadShowing = 0;
        }

        // Hide the soft keyboard
        KeyboardUtils.hide(this, getCurrentFocus());

        showBackButton(true);
    }

    @Override
    public void onOpened() {
        // When the menu (i.e. the conversation list) has been opened, the content has been opened.
        // So notify the content fragment.
        if (mContent != null) mContent.onContentClosed();
    }

    @Override
    public void onClosed() {
        // When the menu (i.e. the conversation list) has been closed, the content has been opened.
        // So notify the content fragment.
        if (mContent != null && ((Fragment) mContent).isAdded()) mContent.onContentOpened();
    }

    @Override
    public void onChanging(float percentOpen) {
        if (mContent != null) mContent.onMenuChanging(percentOpen);
    }

    /**
     * Build and show the proper delete thread dialog. The UI is slightly different
     * depending on whether there are locked messages in the thread(s) and whether we're
     * deleting single/multiple threads or all threads.
     *
     * @param listener          gets called when the delete button is pressed
     * @param threadIds         the thread IDs to be deleted (pass null for all threads)
     * @param hasLockedMessages whether the thread(s) contain locked messages
     * @param context           used to load the various UI elements
     */
    public static void confirmDeleteThreadDialog(final DeleteThreadListener listener, Collection<Long> threadIds,
                                                 boolean hasLockedMessages, Context context) {
        View contents = View.inflate(context, R.layout.dialog_delete_thread, null);
        android.widget.TextView msg = (android.widget.TextView) contents.findViewById(R.id.message);

        if (threadIds == null) {
            msg.setText(R.string.confirm_delete_all_conversations);
        } else {
            // Show the number of threads getting deleted in the confirmation dialog.
            int cnt = threadIds.size();
            msg.setText(context.getResources().getQuantityString(
                    R.plurals.confirm_delete_conversation, cnt, cnt));
        }

        final CheckBox checkbox = (CheckBox) contents.findViewById(R.id.delete_locked);
        if (!hasLockedMessages) {
            checkbox.setVisibility(View.GONE);
        } else {
            listener.setDeleteLockedMessage(checkbox.isChecked());
            checkbox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.setDeleteLockedMessage(checkbox.isChecked());
                }
            });
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.confirm_dialog_title)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setCancelable(true)
                .setPositiveButton(R.string.delete, listener)
                .setNegativeButton(R.string.cancel, null)
                .setView(contents)
                .show();
    }

    public static class DeleteThreadListener implements DialogInterface.OnClickListener {
        private final Collection<Long> mThreadIds;
        private final Conversation.ConversationQueryHandler mHandler;
        private final Context mContext;
        private boolean mDeleteLockedMessages;

        public DeleteThreadListener(Collection<Long> threadIds, Conversation.ConversationQueryHandler handler, Context context) {
            mThreadIds = threadIds;
            mHandler = handler;
            mContext = context;
        }

        public void setDeleteLockedMessage(boolean deleteLockedMessages) {
            mDeleteLockedMessages = deleteLockedMessages;
        }

        @Override
        public void onClick(DialogInterface dialog, final int whichButton) {
            MessageUtils.handleReadReport(mContext, mThreadIds,
                    PduHeaders.READ_STATUS__DELETED_WITHOUT_BEING_READ, () -> {
                        int token = DELETE_CONVERSATION_TOKEN;
                        if (mThreadIds == null) {
                            Conversation.startDeleteAll(mHandler, token, mDeleteLockedMessages);
                            DraftCache.getInstance().refresh();
                        } else {
                            Conversation.startDelete(mHandler, token, mDeleteLockedMessages, mThreadIds);
                        }
                    }
            );
            dialog.dismiss();
        }
    }
}
