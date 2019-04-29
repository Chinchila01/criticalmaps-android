package de.stephanlindauer.criticalmaps.fragments;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.Editable;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import butterknife.OnEditorAction;
import butterknife.Unbinder;
import com.squareup.otto.Subscribe;

import org.ligi.axt.AXT;
import org.ligi.axt.simplifications.SimpleTextWatcher;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import de.stephanlindauer.criticalmaps.App;
import de.stephanlindauer.criticalmaps.R;
import de.stephanlindauer.criticalmaps.adapter.ChatMessageAdapter;
import de.stephanlindauer.criticalmaps.events.NetworkConnectivityChangedEvent;
import de.stephanlindauer.criticalmaps.events.NewLocationEvent;
import de.stephanlindauer.criticalmaps.events.NewServerResponseEvent;
import de.stephanlindauer.criticalmaps.interfaces.IChatMessage;
import de.stephanlindauer.criticalmaps.model.ChatModel;
import de.stephanlindauer.criticalmaps.model.OwnLocationModel;
import de.stephanlindauer.criticalmaps.provider.EventBus;
import de.stephanlindauer.criticalmaps.model.chat.OutgoingChatMessage;

public class ChatFragment extends Fragment {

    //dependencies
    @Inject
    ChatModel chatModel;

    @Inject
    EventBus eventBus;

    @Inject
    OwnLocationModel ownLocationModel;

    //view
    @BindView(R.id.chat_recycler)
    RecyclerView chatRecyclerView;

    @BindView(R.id.text_input_layout)
    TextInputLayout textInputLayout;

    @BindView(R.id.chat_edit_message)
    EditText editMessageTextField;

    @BindView(R.id.chat_send_btn)
    FloatingActionButton sendButton;

    //misc
    private boolean isTextInputEnabled = true;
    private boolean isDataConnectionAvailable = true;
    private ChatMessageAdapter chatMessageAdapter;
    private Unbinder unbinder;


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        App.components().inject(this);
        View chatView = inflater.inflate(R.layout.fragment_chat, container, false);
        unbinder = ButterKnife.bind(this, chatView);

        chatRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        return chatView;
    }

    @Override
    public void onActivityCreated(final Bundle savedState) {
        super.onActivityCreated(savedState);

        chatMessageAdapter = new ChatMessageAdapter(new ArrayList<>());
        chatRecyclerView.setAdapter(chatMessageAdapter);
        displayNewData();

        textInputLayout.setCounterMaxLength(IChatMessage.MAX_LENGTH);
        editMessageTextField.setFilters(new InputFilter[]{new InputFilter.LengthFilter(IChatMessage.MAX_LENGTH)});
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);

        sendButton.setEnabled(editMessageTextField.getText().length() > 0);

        editMessageTextField.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                setSendButtonEnabledWithAnimation(s.length() > 0);
            }
        });
    }

    private void setSendButtonEnabledWithAnimation(final boolean enabled) {
        if (sendButton.isEnabled() == enabled) {
            return;
        }

        final AnimatorSet animatorSet = (AnimatorSet) AnimatorInflater.loadAnimator(getActivity(),
                R.animator.chat_fab_state_change);
        animatorSet.setTarget(sendButton);

        // flip button state for color change after first half of the animation
        final ArrayList<Animator> animations = animatorSet.getChildAnimations();
        animations.get(0).addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                sendButton.setEnabled(enabled);
            }
        });

        animatorSet.start();
    }

    @OnEditorAction(R.id.chat_edit_message)
    boolean handleEditorAction(int actionId) {
        if (actionId == EditorInfo.IME_ACTION_SEND) {
            handleSendClicked();
            return true;
        }
        return false;
    }

    @OnClick(R.id.chat_send_btn)
    void handleSendClicked() {
        String message = editMessageTextField.getText().toString();

        if (message.isEmpty()) {
            return;
        }

        chatModel.setNewOutgoingMessage(new OutgoingChatMessage(message));

        editMessageTextField.setText("");
        displayNewData();
    }

    private void displayNewData() {
        List<IChatMessage> savedAndOutgoingMessages = chatModel.getSavedAndOutgoingMessages();
        chatMessageAdapter.updateData(savedAndOutgoingMessages);

        if (chatRecyclerView.getScrollState() ==  RecyclerView.SCROLL_STATE_IDLE) {
            chatRecyclerView.scrollToPosition(savedAndOutgoingMessages.size() - 1);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        displayNewData();
        eventBus.register(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        eventBus.unregister(this);
        AXT.at(editMessageTextField).hideKeyBoard();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }

    @Subscribe
    public void handleNewServerData(NewServerResponseEvent e) {
        displayNewData();
    }

    @Subscribe
    public void handleNewLocation(NewLocationEvent e) {
        setTextInputState(ownLocationModel.ownLocation != null, isDataConnectionAvailable);
    }

    @Subscribe
    public void handleNetworkConnectivityChanged(NetworkConnectivityChangedEvent e) {
        isDataConnectionAvailable = e.isConnected;
        setTextInputState(ownLocationModel.ownLocation != null, isDataConnectionAvailable);
    }

    private void setTextInputState(final boolean locationKnown, final boolean dataEnabled) {
        if (!locationKnown || !dataEnabled) {
            // FIXME fix this hacky mess of state handling
            setSendButtonEnabledWithAnimation(false);
            editMessageTextField.setEnabled(false);
            if (dataEnabled) {
                textInputLayout.setHint(getString(R.string.map_searching_for_location));
            } else {
                textInputLayout.setHint(getString(R.string.chat_no_data_connection_hint));
            }
            isTextInputEnabled = false;
        } else if (!isTextInputEnabled) {
            if (editMessageTextField.getText().length() > 0) {
                setSendButtonEnabledWithAnimation(true);
            }
            editMessageTextField.setEnabled(true);
            textInputLayout.setHint(getString(R.string.chat_text));
            isTextInputEnabled = true;
        }
    }
}
