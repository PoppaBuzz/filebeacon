package com.jphat.filebeacon

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ThemeSelectionDialog(private val context: Context, private val onThemeSelected: (ThemeManager.Theme) -> Unit) {
    
    fun show() {
        val themes = ThemeManager.Theme.values()
        val currentTheme = ThemeManager.getCurrentTheme(context)
        
        val adapter = ThemeAdapter(themes.toList(), currentTheme) { theme ->
            onThemeSelected(theme)
        }
        
        val recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
            setPadding(0, 16, 0, 16)
        }
        
        AlertDialog.Builder(context)
            .setTitle("Select Theme")
            .setView(recyclerView)
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private class ThemeAdapter(
        private val themes: List<ThemeManager.Theme>,
        private val currentTheme: ThemeManager.Theme,
        private val onThemeClick: (ThemeManager.Theme) -> Unit
    ) : RecyclerView.Adapter<ThemeAdapter.ThemeViewHolder>() {
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(
                R.layout.item_theme_selection,
                parent,
                false
            )
            return ThemeViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ThemeViewHolder, position: Int) {
            val theme = themes[position]
            val colors = ThemeManager.getThemeColors(theme)
            
            holder.themeName.text = theme.displayName
            holder.themeName.setTextColor(colors.onSurface)
            
            // Set color previews
            holder.colorPrimary.setBackgroundColor(colors.primary)
            holder.colorSecondary.setBackgroundColor(colors.secondary)
            holder.colorBackground.setBackgroundColor(colors.background)
            
            // Show selected indicator
            holder.selectedIndicator.visibility = if (theme == currentTheme) View.VISIBLE else View.GONE
            
            // Create colored card background with border for selected theme
            val drawable = GradientDrawable().apply {
                setColor(colors.surface)
                cornerRadius = 12f
                if (theme == currentTheme) {
                    setStroke(6, colors.primary)
                }
            }
            holder.themeCard.background = drawable
            
            holder.itemView.setOnClickListener {
                onThemeClick(theme)
            }
        }
        
        override fun getItemCount() = themes.size
        
        class ThemeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val themeCard: View = view.findViewById(R.id.themeCard)
            val themeName: TextView = view.findViewById(R.id.themeName)
            val colorPrimary: View = view.findViewById(R.id.colorPrimary)
            val colorSecondary: View = view.findViewById(R.id.colorSecondary)
            val colorBackground: View = view.findViewById(R.id.colorBackground)
            val selectedIndicator: View = view.findViewById(R.id.selectedIndicator)
        }
    }
}
