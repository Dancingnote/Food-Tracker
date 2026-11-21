package com.comp9003.foodtracker;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;

public class FamilyFragment extends Fragment {

    // ── SharedPrefs keys ──────────────────────────────────────────────
    static final String PREFS_FAMILY        = "family_prefs";
    static final String KEY_FAMILY_CODE     = "family_code_";       // + username
    static final String KEY_MEMBERS         = "family_members_";    // + familyCode → JSON array of usernames
    static final String KEY_JOINED_CODE     = "joined_code_";       // + username  → familyCode
    static final String KEY_MESSAGES        = "family_messages_";   // + familyCode → JSON array
    private static final String KEY_LAST_READ = "last_read_";       // + username + "_" + familyCode → int

    // ── State ──────────────────────────────────────────────────────────
    private String currentUser;
    private String activeFamilyCode;   // code of the family the current user is in (owner's code)
    private boolean isOwner;

    // ── UI ────────────────────────────────────────────────────────────
    private TextView tvFamilyCode, tvFamilyCodeHint;
    private TextView tabMembers, tabChat;
    private TextView tvTabChatBadge;
    private View panelMembers, panelChat;
    private View llChatInput;

    private MembersAdapter membersAdapter;
    private ChatAdapter chatAdapter;
    private final List<String>      memberList   = new ArrayList<>();
    private final List<ChatMessage> messageList  = new ArrayList<>();

    private EditText etMessage;

    // ── Lifecycle ─────────────────────────────────────────────────────
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_share, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        currentUser = ProductRepository.getCurrentUser(requireContext());
        if (currentUser == null) currentUser = "guest";

        tvFamilyCode     = view.findViewById(R.id.tv_family_code);
        tvFamilyCodeHint = view.findViewById(R.id.tv_family_code_hint);
        tabMembers       = view.findViewById(R.id.tab_members);
        tabChat          = view.findViewById(R.id.tab_chat);
        tvTabChatBadge   = view.findViewById(R.id.tab_chat_badge);
        panelMembers     = view.findViewById(R.id.panel_members);
        panelChat        = view.findViewById(R.id.panel_chat);
        llChatInput      = view.findViewById(R.id.ll_chat_input);
        etMessage        = view.findViewById(R.id.et_message);

        // Determine which family this user belongs to
        loadFamilyState();

        // Members RecyclerView
        RecyclerView rvMembers = view.findViewById(R.id.rv_members);
        membersAdapter = new MembersAdapter();
        rvMembers.setLayoutManager(new LinearLayoutManager(getContext()));
        rvMembers.setAdapter(membersAdapter);
        refreshMembers();

        // Chat RecyclerView
        RecyclerView rvChat = view.findViewById(R.id.rv_chat);
        chatAdapter = new ChatAdapter();
        rvChat.setLayoutManager(new LinearLayoutManager(getContext()));
        rvChat.setAdapter(chatAdapter);
        refreshChat();
        scrollChatToBottom(rvChat);

        // Family code display + copy
        tvFamilyCode.setText(activeFamilyCode != null ? activeFamilyCode : "—");
        if (activeFamilyCode != null) {
            tvFamilyCodeHint.setText(isOwner ? "You are the family owner" : "You have joined a family");
        } else {
            tvFamilyCodeHint.setText("Create your family or join one below");
        }

        view.findViewById(R.id.btn_copy_code).setOnClickListener(v -> {
            String code = activeFamilyCode != null ? activeFamilyCode : generateCode();
            if (activeFamilyCode == null) {
                activeFamilyCode = code;
                isOwner = true;
                saveOwnerCode(code);
                tvFamilyCode.setText(code);
                tvFamilyCodeHint.setText("You are the family owner");
                refreshMembers();
            }
            ClipboardManager cm = (ClipboardManager) requireContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("family_code", code));
            Toast.makeText(getContext(), "Code copied!", Toast.LENGTH_SHORT).show();
        });

        // Join family
        EditText etJoin = view.findViewById(R.id.et_join_code);
        view.findViewById(R.id.btn_join_family).setOnClickListener(v -> {
            String code = etJoin.getText().toString().trim().toUpperCase();
            if (code.length() < 4) {
                Toast.makeText(getContext(), "Please enter a valid code", Toast.LENGTH_SHORT).show();
                return;
            }
            joinFamily(code);
            etJoin.setText("");
            hideKeyboard(etJoin);
        });

        // Tabs
        tabMembers.setOnClickListener(v -> switchTab(true));
        tabChat.setOnClickListener(v -> switchTab(false));

        // Show initial tab badge if there are unread messages
        updateTabBadge();

        // Send message
        view.findViewById(R.id.btn_send).setOnClickListener(v -> sendMessage(rvChat));
        etMessage.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage(rvChat);
                return true;
            }
            return false;
        });
    }

    // ── Family State ─────────────────────────────────────────────────
    private void loadFamilyState() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_FAMILY, Context.MODE_PRIVATE);
        // Check if user owns a family
        String ownCode = prefs.getString(KEY_FAMILY_CODE + currentUser, null);
        if (ownCode != null) {
            activeFamilyCode = ownCode;
            isOwner = true;
            return;
        }
        // Check if user joined a family
        String joinedCode = prefs.getString(KEY_JOINED_CODE + currentUser, null);
        if (joinedCode != null) {
            activeFamilyCode = joinedCode;
            isOwner = false;
        }
    }

    private void saveOwnerCode(String code) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_FAMILY, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_FAMILY_CODE + currentUser, code).apply();
        // Add self to members list
        addMemberToCode(code, currentUser);
    }

    private void joinFamily(String code) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_FAMILY, Context.MODE_PRIVATE);
        // Check code belongs to someone
        String membersJson = prefs.getString(KEY_MEMBERS + code, null);
        if (membersJson == null) {
            Toast.makeText(getContext(), "Family code not found", Toast.LENGTH_SHORT).show();
            return;
        }
        if (activeFamilyCode != null) {
            Toast.makeText(getContext(), "You are already in a family", Toast.LENGTH_SHORT).show();
            return;
        }
        // Join
        addMemberToCode(code, currentUser);
        prefs.edit().putString(KEY_JOINED_CODE + currentUser, code).apply();
        activeFamilyCode = code;
        isOwner = false;
        tvFamilyCode.setText(code);
        tvFamilyCodeHint.setText("You have joined a family");
        refreshMembers();
        Toast.makeText(getContext(), "Joined family!", Toast.LENGTH_SHORT).show();
    }

    private void addMemberToCode(String code, String username) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_FAMILY, Context.MODE_PRIVATE);
        try {
            String json = prefs.getString(KEY_MEMBERS + code, "[]");
            JSONArray arr = new JSONArray(json);
            // Check duplicate
            for (int i = 0; i < arr.length(); i++) {
                if (username.equals(arr.optString(i))) return;
            }
            arr.put(username);
            prefs.edit().putString(KEY_MEMBERS + code, arr.toString()).apply();
        } catch (Exception e) {
            SharedPreferences.Editor ed = prefs.edit();
            ed.putString(KEY_MEMBERS + code, "[\"" + username + "\"]");
            ed.apply();
        }
    }

    private void removeMember(String username) {
        if (!isOwner || activeFamilyCode == null) return;
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_FAMILY, Context.MODE_PRIVATE);
        try {
            String json = prefs.getString(KEY_MEMBERS + activeFamilyCode, "[]");
            JSONArray arr = new JSONArray(json);
            JSONArray updated = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                if (!username.equals(arr.optString(i))) updated.put(arr.optString(i));
            }
            prefs.edit().putString(KEY_MEMBERS + activeFamilyCode, updated.toString()).apply();
            // Clear the join reference for that user
            prefs.edit().remove(KEY_JOINED_CODE + username).apply();
        } catch (Exception ignored) {}
        refreshMembers();
    }

    // ── Members ───────────────────────────────────────────────────────
    private void refreshMembers() {
        memberList.clear();
        if (activeFamilyCode == null) {
            memberList.add(currentUser); // just self
        } else {
            SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_FAMILY, Context.MODE_PRIVATE);
            try {
                String json = prefs.getString(KEY_MEMBERS + activeFamilyCode, "[]");
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    String u = arr.optString(i, null);
                    if (u != null) memberList.add(u);
                }
            } catch (Exception ignored) {}
            if (!memberList.contains(currentUser)) memberList.add(0, currentUser);
        }
        if (membersAdapter != null) membersAdapter.notifyDataSetChanged();
    }

    // ── Chat ─────────────────────────────────────────────────────────
    private void sendMessage(RecyclerView rv) {
        if (activeFamilyCode == null) {
            Toast.makeText(getContext(), "Join or create a family first", Toast.LENGTH_SHORT).show();
            return;
        }
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        ChatMessage msg = new ChatMessage(currentUser, text,
                new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        saveMessage(msg);
        etMessage.setText("");
        refreshChat();
        scrollChatToBottom(rv);
        hideKeyboard(etMessage);
    }

    private void saveMessage(ChatMessage msg) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_FAMILY, Context.MODE_PRIVATE);
        try {
            String json = prefs.getString(KEY_MESSAGES + activeFamilyCode, "[]");
            JSONArray arr = new JSONArray(json);
            JSONObject obj = new JSONObject();
            obj.put("sender", msg.sender);
            obj.put("text", msg.text);
            obj.put("time", msg.time);
            arr.put(obj);
            // Keep last 200 messages
            while (arr.length() > 200) arr.remove(0);
            prefs.edit().putString(KEY_MESSAGES + activeFamilyCode, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void refreshChat() {
        messageList.clear();
        if (activeFamilyCode == null) {
            if (chatAdapter != null) chatAdapter.notifyDataSetChanged();
            return;
        }
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_FAMILY, Context.MODE_PRIVATE);
        try {
            String json = prefs.getString(KEY_MESSAGES + activeFamilyCode, "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj != null) {
                    messageList.add(new ChatMessage(
                            obj.optString("sender"),
                            obj.optString("text"),
                            obj.optString("time")));
                }
            }
        } catch (Exception ignored) {}
        if (chatAdapter != null) chatAdapter.notifyDataSetChanged();
    }

    private void scrollChatToBottom(RecyclerView rv) {
        if (chatAdapter != null && chatAdapter.getItemCount() > 0) {
            rv.post(() -> rv.scrollToPosition(chatAdapter.getItemCount() - 1));
        }
    }

    // ── Tab Switching ─────────────────────────────────────────────────
    private void switchTab(boolean membersActive) {
        panelMembers.setVisibility(membersActive ? View.VISIBLE : View.GONE);
        panelChat.setVisibility(membersActive ? View.GONE : View.VISIBLE);
        llChatInput.setVisibility(membersActive ? View.GONE : View.VISIBLE);
        tabMembers.setTextColor(membersActive ? 0xFFE58332 : 0xFF8A7B6E);
        tabChat.setTextColor(membersActive ? 0xFF8A7B6E : 0xFFE58332);
        tabMembers.setBackgroundResource(membersActive
                ? R.drawable.tab_indicator_selected : R.drawable.tab_indicator_unselected);
        tabChat.setBackgroundResource(membersActive
                ? R.drawable.tab_indicator_unselected : R.drawable.tab_indicator_selected);

        if (!membersActive) {
            // User opened the chat — mark all messages as read
            markChatRead();
            updateTabBadge();          // hide tab badge
            clearNavBadge();           // hide bottom-nav badge
        }
    }

    // ── Unread Badge ──────────────────────────────────────────────────

    /** Returns unread message count for the given user. Called by MainActivity. */
    public static int getUnreadCount(Context context, String currentUser) {
        if (currentUser == null) return 0;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FAMILY, Context.MODE_PRIVATE);
        String familyCode = prefs.getString(KEY_FAMILY_CODE + currentUser, null);
        if (familyCode == null) familyCode = prefs.getString(KEY_JOINED_CODE + currentUser, null);
        if (familyCode == null) return 0;
        int lastRead = prefs.getInt(KEY_LAST_READ + currentUser + "_" + familyCode, 0);

        int unread = 0;
        try {
            String json = prefs.getString(KEY_MESSAGES + familyCode, "[]");
            JSONArray arr = new JSONArray(json);
            int start = Math.max(0, Math.min(lastRead, arr.length()));
            for (int i = start; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj == null) continue;
                String sender = obj.optString("sender", null);
                if (sender == null) continue;
                if (!sender.equalsIgnoreCase(currentUser)) unread++;
            }
        } catch (Exception ignored) {}

        return unread;
    }

    /** Saves current message count as "read" baseline for this user. */
    private void markChatRead() {
        if (activeFamilyCode == null) return;
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_FAMILY, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_LAST_READ + currentUser + "_" + activeFamilyCode,
                messageList.size()).apply();
    }

    /** Updates the badge on the Group Chat tab. */
    private void updateTabBadge() {
        if (tvTabChatBadge == null) return;
        int unread = getUnreadCount(requireContext(), currentUser);
        if (unread > 0) {
            tvTabChatBadge.setText(unread > 99 ? "99+" : String.valueOf(unread));
            tvTabChatBadge.setVisibility(View.VISIBLE);
        } else {
            tvTabChatBadge.setVisibility(View.GONE);
        }
    }

    /** Hides the unread badge on the bottom navigation bar. */
    private void clearNavBadge() {
        if (getActivity() == null) return;
        View badge = getActivity().findViewById(R.id.nav_chat_badge);
        if (badge != null) badge.setVisibility(View.GONE);
    }

    // ── Helpers ───────────────────────────────────────────────────────
    private String generateCode() {
        String base = currentUser.toUpperCase(Locale.US).replaceAll("[^A-Z0-9]", "");
        if (base.length() > 4) base = base.substring(0, 4);
        else if (base.length() < 4) base = (base + "XXXX").substring(0, 4);
        int rnd = new Random().nextInt(9000) + 1000;
        return base + rnd;
    }

    private void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) requireContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    // ── Data Models ───────────────────────────────────────────────────
    static class ChatMessage {
        final String sender, text, time;
        ChatMessage(String sender, String text, String time) {
            this.sender = sender; this.text = text; this.time = time;
        }
    }

    // ── Adapters ──────────────────────────────────────────────────────
    class MembersAdapter extends RecyclerView.Adapter<MembersAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            TextView  tvAvatar, tvName, tvRole, btnRemove;
            android.widget.ImageView ivAvatar;
            VH(View v) {
                super(v);
                tvAvatar  = v.findViewById(R.id.tv_avatar);
                ivAvatar  = v.findViewById(R.id.iv_avatar);
                tvName    = v.findViewById(R.id.tv_member_name);
                tvRole    = v.findViewById(R.id.tv_member_role);
                btnRemove = v.findViewById(R.id.btn_remove_member);
            }
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_family_member, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            String username    = memberList.get(position);
            String displayName = getDisplayName(username);
            String initial     = displayName.isEmpty() ? "?" : displayName.substring(0, 1).toUpperCase();

            // Coloured initial circle (shown when no avatar photo)
            int[] colors = {0xFFE58332, 0xFF4CAF50, 0xFF1565C0, 0xFFAB47BC, 0xFF00897B};
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(colors[Math.abs(username.hashCode()) % colors.length]);
            h.tvAvatar.setBackground(gd);
            h.tvAvatar.setText(initial);

            // Load real avatar photo if available
            String avatarUri = getAvatarUri(username);
            if (avatarUri != null && !avatarUri.isEmpty()) {
                try {
                    h.ivAvatar.setImageURI(android.net.Uri.parse(avatarUri));
                    h.ivAvatar.setVisibility(View.VISIBLE);
                } catch (Exception e) {
                    h.ivAvatar.setVisibility(View.GONE);
                }
            } else {
                h.ivAvatar.setVisibility(View.GONE);
            }

            h.tvName.setText(displayName);

            if (username.equals(currentUser)) {
                h.tvRole.setText("You" + (isOwner ? " · Owner" : ""));
                h.btnRemove.setVisibility(View.GONE);
            } else {
                h.tvRole.setText(activeFamilyCode != null && username.equals(getFamilyOwner()) ? "Owner" : "Member");
                h.btnRemove.setVisibility(isOwner ? View.VISIBLE : View.GONE);
                h.btnRemove.setOnClickListener(v -> removeMember(username));
            }
        }

        @Override public int getItemCount() { return memberList.size(); }
    }

    class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            TextView tvSender, tvMessage, tvTime;
            LinearLayout llBubbleRow;
            VH(View v) {
                super(v);
                tvSender    = v.findViewById(R.id.tv_sender);
                tvMessage   = v.findViewById(R.id.tv_message);
                tvTime      = v.findViewById(R.id.tv_time);
                llBubbleRow = v.findViewById(R.id.ll_bubble_row);
            }
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_message, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            ChatMessage msg = messageList.get(position);
            boolean isMine = msg.sender.equals(currentUser);

            if (isMine) {
                h.tvSender.setVisibility(View.GONE);
                h.tvMessage.setBackgroundResource(R.drawable.msg_bubble_mine);
                h.llBubbleRow.setGravity(Gravity.END);
            } else {
                h.tvSender.setVisibility(View.VISIBLE);
                h.tvSender.setText(getDisplayName(msg.sender));
                h.tvMessage.setBackgroundResource(R.drawable.msg_bubble_other);
                h.llBubbleRow.setGravity(Gravity.START);
            }

            h.tvMessage.setText(msg.text);
            h.tvTime.setText(msg.time);
        }

        @Override public int getItemCount() { return messageList.size(); }
    }

    /** Returns nickname > displayUsername > username, whichever is non-empty first. */
    private String getDisplayName(String username) {
        try {
            com.comp9003.foodtracker.db.UserProfileEntity profile =
                    com.comp9003.foodtracker.db.AppDatabase
                            .getInstance(requireContext())
                            .userProfileDao()
                            .getByUsername(username);
            if (profile != null) {
                if (profile.nickname != null && !profile.nickname.trim().isEmpty())
                    return profile.nickname.trim();
                if (profile.displayUsername != null && !profile.displayUsername.trim().isEmpty())
                    return profile.displayUsername.trim();
            }
        } catch (Exception ignored) {}
        return username;
    }

    private String getAvatarUri(String username) {
        try {
            com.comp9003.foodtracker.db.UserProfileEntity profile =
                    com.comp9003.foodtracker.db.AppDatabase
                            .getInstance(requireContext())
                            .userProfileDao()
                            .getByUsername(username);
            if (profile != null) return profile.avatarUri;
        } catch (Exception ignored) {}
        return null;
    }

    // ── Utility ───────────────────────────────────────────────────────
    private String getFamilyOwner() {
        // The owner's code equals the activeFamilyCode
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_FAMILY, Context.MODE_PRIVATE);
        for (String member : memberList) {
            String code = prefs.getString(KEY_FAMILY_CODE + member, null);
            if (activeFamilyCode.equals(code)) return member;
        }
        return null;
    }
}
