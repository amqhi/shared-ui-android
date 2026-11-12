/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.shared.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.amqhi.shared.core.User
import com.amqhi.shared.ui.databinding.ItemAccountBinding
import com.amqhi.shared.ui.databinding.ItemAddAccountBinding
import com.amqhi.shared.ui.databinding.SheetAccountsBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

data class AccountSheetColors(
    val surfaceColor: Int,
    val textColorPrimary: Int,
    val textColorSecondary: Int,
    val avatarBackgroundColor: Int,
    val avatarTextColor: Int,
)

fun showAccountSheet(
    context: Context,
    layoutInflater: LayoutInflater,
    current: User,
    others: List<User>,
    colors: AccountSheetColors,
    onSelect: (User) -> Unit,
    onAdd: () -> Unit,
) {
    val dialog = BottomSheetDialog(context, R.style.Theme_Anemoneo_BottomSheet)
    val content = SheetAccountsBinding.inflate(layoutInflater)
    content.background.backgroundTintList = ColorStateList.valueOf(colors.surfaceColor)
    bindAccountItem(layoutInflater, content, current, colors)
    others.forEach { user ->
        val item = bindAccountItem(layoutInflater, content, user, colors)
        item.account.setOnClickListener {
            onSelect(user)
            dialog.dismiss()
        }
    }
    val addAccount = ItemAddAccountBinding.inflate(
        layoutInflater,
        content.accounts,
        true,
    )
    addAccount.addAccountButton.setOnClickListener {
        onAdd()
        dialog.dismiss()
    }
    dialog.setContentView(content.root)
    dialog.show()
}

private fun bindAccountItem(
    layoutInflater: LayoutInflater,
    content: SheetAccountsBinding,
    user: User,
    colors: AccountSheetColors,
    showAvatar: Boolean = true,
): ItemAccountBinding {
    val item = ItemAccountBinding.inflate(layoutInflater, content.accounts, true)
    item.email.text = user.email
    item.email.setTextColor(colors.textColorSecondary)
    item.nickname.text = user.name
    item.nickname.setTextColor(colors.textColorPrimary)
    item.avatar.isVisible = showAvatar
    val textStart = if (showAvatar) (52 * item.root.resources.displayMetrics.density).toInt() else 0
    listOf(item.email, item.nickname).forEach { label ->
        label.layoutParams = (label.layoutParams as ViewGroup.MarginLayoutParams).apply {
            marginStart = textStart
        }
    }
    if (showAvatar) {
        item.avatar.setAvatar(
            name = user.name,
            picturePath = user.cachedPicturePath,
            backgroundColor = colors.avatarBackgroundColor,
            textColor = colors.avatarTextColor,
        )
    }
    return item
}
