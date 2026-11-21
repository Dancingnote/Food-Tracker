package com.comp9003.foodtracker;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

public class ProfileFragment extends Fragment {
    private ImageView ivAvatar;
    private TextView tvWelcome;
    private TextView tvLogin;
    private View rowLogout;
    private View dividerLogout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ivAvatar = view.findViewById(R.id.iv_profile_avatar);
        tvWelcome = view.findViewById(R.id.tvWelcome);
        tvLogin = view.findViewById(R.id.tvLogin);
        dividerLogout = view.findViewById(R.id.dividerLogout);
        rowLogout = view.findViewById(R.id.rowLogout);

        ivAvatar.setOnClickListener(v -> startActivity(new Intent(requireContext(), AvatarEditActivity.class)));
        tvLogin.setOnClickListener(v -> {
            if (ProductRepository.isLoggedIn(requireContext())) {
                startActivity(new Intent(requireContext(), EditProfileActivity.class));
            } else {
                startActivity(new Intent(requireContext(), LoginActivity.class));
            }
        });

        rowLogout.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Sign out")
                    .setMessage("Are you sure you want to sign out?")
                    .setNegativeButton("Cancel", (d, which) -> d.dismiss())
                    .setPositiveButton("Sign out", (d, which) -> {
                        ProductRepository.logout(requireContext());
                        if (getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).startLoginFlow();
                        } else {
                            startActivity(new Intent(requireContext(), LoginActivity.class));
                        }
                    })
                    .show();
        });

        refreshHeader();

        LinearLayout rowDisplayPrefs = view.findViewById(R.id.rowDisplayPrefs);
        rowDisplayPrefs.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), DisplayPreferencesActivity.class))
        );

        LinearLayout rowReminder = view.findViewById(R.id.rowReminder);
        rowReminder.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), ReminderSettingsActivity.class))
        );

        LinearLayout rowNearExpiryConfig = view.findViewById(R.id.rowNearExpiryConfig);
        rowNearExpiryConfig.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), NearExpiryConfigActivity.class))
        );

        LinearLayout rowExpirySettings = view.findViewById(R.id.rowExpirySettings);
        rowExpirySettings.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), ExpirySettingsActivity.class))
        );

        LinearLayout rowHelpFeedback = view.findViewById(R.id.rowHelpFeedback);
        rowHelpFeedback.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), HelpFeedbackActivity.class))
        );
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshHeader();
    }

    private void refreshHeader() {
        if (!isAdded()) return;

        if (tvWelcome != null) {
            tvWelcome.setText("Welcome to Food Tracker");
        }

        if (ivAvatar != null) {
            String avatar = ProductRepository.getAvatarUri(requireContext());
            if (avatar != null) {
                try {
                    ivAvatar.setImageURI(Uri.parse(avatar));
                } catch (Exception ignored) {
                    ivAvatar.setImageResource(R.drawable.profile_placeholder);
                }
            } else {
                ivAvatar.setImageResource(R.drawable.profile_placeholder);
            }
        }

        if (tvLogin != null) {
            if (ProductRepository.isLoggedIn(requireContext())) {
                tvLogin.setText("Edit Profile ›");
            } else {
                tvLogin.setText("Sign In ›");
            }
        }

        boolean loggedIn = ProductRepository.isLoggedIn(requireContext());
        if (rowLogout != null) {
            rowLogout.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        }
        if (dividerLogout != null) {
            dividerLogout.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        }
    }
}
