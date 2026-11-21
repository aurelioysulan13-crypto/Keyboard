
package com.custom.keyboard;

import android.content.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Unicode emoji data for the keyboard.
 *
 * Emoji are not drawn using a bundled font. They are displayed as Unicode
 * characters so Android can use the device's own emoji font and renderer.
 */
final class EmojiData {

    private EmojiData() {}

    static final int COUNT = 9;

    static final String[] NAMES = {
            "Recent",
            "Smileys & People",
            "Animals & Nature",
            "Food & Drink",
            "Activities",
            "Travel & Places",
            "Objects",
            "Symbols",
            "Flags"
    };

    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor();

    private static volatile boolean ready = false;
    private static volatile List<List<String>> categories =
            Collections.emptyList();

    static void prepareAsync(Context context) {
        if (ready) return;

        synchronized (EmojiData.class) {
            if (ready) return;

            EXECUTOR.execute(new Runnable() {
                @Override
                public void run() {
                    ArrayList<List<String>> data = new ArrayList<>();

                    // Index 0 is reserved for recently used emoji.
                    data.add(Collections.<String>emptyList());

                    data.add(makeList(
                            "😀 😃 😄 😁 😆 😅 😂 🙂 🙃 🫠 😉 😊 😇 🥰 😍 🤩 "
                          + "😘 😗 ☺️ 😚 😙 🥲 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🫢 🫣 "
                          + "🤫 🤔 🫡 🤐 🤨 😐 😑 😶 🫥 😶‍🌫️ 😏 😒 🙄 😬 😮‍💨 "
                          + "🤥 🫨 🙂‍↔️ 🙂‍↕️ 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 "
                          + "🤧 🥵 🥶 🥴 😵 😵‍💫 🤯 🤠 🥳 🥸 😎 🤓 🧐 😕 🫤 "
                          + "😟 🙁 ☹️ 😮 😯 😲 😳 🥺 🥹 😦 😧 😨 😰 😥 😢 "
                          + "😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿 "
                          + "💀 ☠️ 💩 🤡 👹 👺 👻 👽 👾 🤖 🎃"
                    ));

                    data.add(makeList(
                            "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐻‍❄️ 🐨 🐯 🦁 "
                          + "🐮 🐷 🐽 🐸 🐵 🙈 🙉 🙊 🐒 🦍 🦧 🐔 🐧 "
                          + "🐦 🐤 🐣 🐥 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 "
                          + "🐝 🪱 🐛 🦋 🐌 🐞 🐜 🪰 🪲 🪳 🦟 🦗 "
                          + "🕷️ 🕸️ 🦂 🐢 🐍 🦎 🦖 🦕 🐙 🦑 🦐 🦞 "
                          + "🦀 🐡 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐅 🐆 🦓 "
                          + "🦍 🦣 🐘 🦛 🦏 🐪 🐫 🦒 🦘 🦬 🐃 🐂 "
                          + "🐄 🐎 🐖 🐏 🐑 🦙 🐐 🦌 🐕 🐩 🦮 🐈 "
                          + "🐓 🦃 🦚 🦜 🦢 🦩 🕊️ 🐇 🦝 🦨 🦡 🦫 "
                          + "🌵 🎄 🌲 🌳 🌴 🪵 🌱 🌿 ☘️ 🍀 🎍 🪴 "
                          + "🎋 🍃 🍂 🍁 🍄 🌾 💐 🌷 🌹 🥀 🌺 🌸 "
                          + "🌼 🌻 🌞 🌝 🌛 🌜 🌚 🌕 🌖 🌗 🌘 🌑 "
                          + "🌒 🌓 🌔 🌙 🌎 🌍 🌏 🪐 💫 ⭐ 🌟 ✨ "
                          + "⚡ ☄️ 💥 🔥 🌪️ 🌈 ☀️ 🌤️ ⛅ 🌥️ ☁️ "
                          + "🌦️ 🌧️ ⛈️ 🌩️ 🌨️ ❄️ ☃️ ⛄ 🌬️ 💨 💧 💦"
                    ));

                    data.add(makeList(
                            "🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍈 🍒 "
                          + "🍑 🥭 🍍 🥥 🥝 🍅 🫒 🥑 🍆 🥔 🥕 🌽 "
                          + "🌶️ 🫑 🥒 🥬 🥦 🧄 🧅 🍄 🥜 🫘 🌰 "
                          + "🍞 🥐 🥖 🫓 🥨 🥯 🥞 🧇 🧀 🍖 🍗 🥩 "
                          + "🥓 🍔 🍟 🍕 🌭 🥪 🌮 🌯 🫔 🥙 🧆 "
                          + "🥚 🍳 🥘 🍲 🫕 🥣 🥗 🍿 🧈 🧂 🥫 "
                          + "🍱 🍘 🍙 🍚 🍛 🍜 🍝 🍠 🍢 🍣 🍤 🍥 "
                          + "🥮 🍡 🥟 🥠 🥡 🦀 🍦 🍧 🍨 🍩 🍪 🎂 "
                          + "🍰 🧁 🥧 🍫 🍬 🍭 🍮 🍯 🍼 🥛 ☕ 🫖 "
                          + "🍵 🍶 🍺 🍻 🥂 🍷 🥃 🍸 🍹 🧉 🍾 🧋 "
                          + "🧃 🥤 🧊"
                    ));

                    data.add(makeList(
                            "⚽ 🏀 🏈 ⚾ 🥎 🎾 🏐 🏉 🥏 🎱 🪀 🏓 "
                          + "🏸 🏒 🏑 🥍 🏏 🪃 🥅 ⛳ 🪁 🏹 🎣 🤿 "
                          + "🥊 🥋 🎽 🛹 🛼 🛷 ⛸️ 🥌 🎿 ⛷️ 🏂 "
                          + "🪂 🏋️ 🤼 🤸 ⛹️ 🤺 🤾 🏌️ 🏇 🧘 🏄 "
                          + "🏊 🤽 🚣 🧗 🚵 🚴 🏆 🥇 🥈 🥉 🏅 🎖️ "
                          + "🏵️ 🎗️ 🎫 🎟️ 🎪 🤹 🎭 🩰 🎨 🎬 🎤 🎧 "
                          + "🎼 🎹 🥁 🪘 🎷 🎺 🪗 🎸 🪕 🎻 🪇 🎲 "
                          + "♟️ 🎯 🎳 🎮 🎰 🧩"
                    ));

                    data.add(makeList(
                            "🚗 🚕 🚙 🚌 🚎 🏎️ 🚓 🚑 🚒 🚐 🛻 🚚 "
                          + "🚛 🚜 🛵 🏍️ 🛺 🚲 🛴 🛹 🛼 🚏 🛣️ "
                          + "🛤️ ⛽ 🛞 🚨 🚥 🚦 🚧 ⚓ 🛟 ⛵ 🚤 "
                          + "🛳️ ⛴️ 🛥️ 🚢 ✈️ 🛩️ 🛫 🛬 🪂 💺 🚁 "
                          + "🚟 🚠 🚡 🚀 🛸 🛰️ 🌍 🌎 🌏 🌐 🗺️ "
                          + "🧭 🏔️ ⛰️ 🌋 🗻 🏕️ 🏖️ 🏜️ 🏝️ 🏞️ "
                          + "🏟️ 🏛️ 🏗️ 🧱 🪨 🪵 🛖 🏘️ 🏚️ 🏠 "
                          + "🏡 🏢 🏣 🏤 🏥 🏦 🏨 🏩 🏪 🏫 "
                          + "🏬 🏭 🏯 🏰 💒 🗼 🗽 ⛪ 🕌 🛕 "
                          + "🕍 ⛩️ 🕋 ⛲ ⛺ 🌁 🌃 🏙️ 🌄 🌅 "
                          + "🌆 🌇 🌉 ♨️ 🎠 🎡 🎢"
                    ));

                    data.add(makeList(
                            "⌚ 📱 📲 💻 🖥️ 🖨️ ⌨️ 🖱️ 🖲️ 💽 💾 "
                          + "💿 📀 🧮 🎥 🎞️ 📽️ 🎬 📺 📷 📸 📹 "
                          + "📼 🔍 🔎 🕯️ 💡 🔦 🏮 🪔 📔 📕 📖 "
                          + "📗 📘 📙 📚 📓 📒 📃 📜 📄 📰 🗞️ "
                          + "📑 🔖 🏷️ 💰 🪙 💴 💵 💶 💷 💸 💳 "
                          + "🧾 💹 ✉️ 📧 📨 📩 📤 📥 📦 📫 📪 "
                          + "📬 📭 📮 🗳️ ✏️ ✒️ 🖋️ 🖊️ 🖌️ 🖍️ "
                          + "📝 💼 📁 📂 🗂️ 📅 📆 🗒️ 🗓️ 📇 "
                          + "📈 📉 📊 📋 📌 📍 📎 🖇️ 📏 📐 "
                          + "✂️ 🗃️ 🗄️ 🗑️ 🔒 🔓 🔏 🔐 🔑 🗝️ "
                          + "🔨 🪓 ⛏️ ⚒️ 🛠️ 🗡️ ⚔️ 🔫 🪃 🛡️ "
                          + "🔧 🪛 🔩 ⚙️ 🗜️ ⚖️ 🦯 🔗 ⛓️ 🪝 "
                          + "🧰 🧲 🪜 🧪 🧫 🧬 🔬 🔭 📡 💉 "
                          + "🩹 🩺 🩻 🚪 🛏️ 🛋️ 🪑 🚽 🚿 🛁 "
                          + "🪒 🧴 🧷 🧹 🧺 🧻 🪣 🧼 🫧 🪥 "
                          + "🧽 🧯 🛒 🚬 ⚰️ 🪦 ⚱️ 🗿"
                    ));

                    data.add(makeList(
                            "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❤️‍🔥 "
                          + "❤️‍🩹 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 "
                          + "☮️ ✝️ ☪️ 🕉️ ☸️ ✡️ 🔯 🕎 ☯️ ☦️ "
                          + "🛐 ⛎ ♈ ♉ ♊ ♋ ♌ ♍ ♎ ♏ ♐ "
                          + "♑ ♒ ♓ 🆔 ⚛️ 🉑 ☢️ ☣️ 📴 📳 "
                          + "🈶 🈚 🈸 🈺 🈷️ ✴️ 🆚 💮 🉐 ㊙️ "
                          + "㊗️ 🈴 🈵 🈹 🈲 🅰️ 🅱️ 🆎 🆑 🅾️ "
                          + "🆘 ❌ ⭕ 🛑 ⛔ 📛 🚫 💯 💢 ♨️ "
                          + "🚷 🚯 🚳 🚱 🔞 📵 🚭 ❗ ❕ ❓ ❔ "
                          + "‼️ ⁉️ 🔅 🔆 〽️ ⚠️ 🚸 🔱 ⚜️ 🔰 "
                          + "♻️ ✅ ☑️ ✔️ ❎ ➕ ➖ ➗ ✖️ ♾️ "
                          + "💲 💱 ™️ ©️ ®️ 〰️ ➰ ➿ 🔚 🔙 🔛 "
                          + "🔝 🔜 🔃 🔄 🔀 🔁 🔂 ▶️ ⏩ ⏭️ "
                          + "⏯️ ◀️ ⏪ ⏮️ 🔼 ⏫ 🔽 ⏬ ⏸️ ⏹️ "
                          + "⏺️ ⏏️ 🎦 🔣 🔤 🔡 🔠 🔢 #️⃣ *️⃣ "
                          + "0️⃣ 1️⃣ 2️⃣ 3️⃣ 4️⃣ 5️⃣ 6️⃣ 7️⃣ 8️⃣ 9️⃣ "
                          + "🔟 🔟 🆕 🆓 🆙 🆒 🆗 🆖 🆗 🆙"
                    ));

                    data.add(makeList(
                            "🏳️ 🏴 🏁 🚩 🏳️‍🌈 🏳️‍⚧️ 🏴‍☠️ "
                          + "🇦🇨 🇦🇩 🇦🇪 🇦🇫 🇦🇬 🇦🇮 🇦🇱 🇦🇲 🇦🇴 🇦🇶 "
                          + "🇦🇷 🇦🇸 🇦🇹 🇦🇺 🇦🇼 🇦🇽 🇦🇿 🇧🇦 🇧🇧 🇧🇩 "
                          + "🇧🇪 🇧🇫 🇧🇬 🇧🇭 🇧🇮 🇧🇯 🇧🇱 🇧🇲 🇧🇳 🇧🇴 "
                          + "🇧🇶 🇧🇷 🇧🇸 🇧🇹 🇧🇻 🇧🇼 🇧🇾 🇧🇿 🇨🇦 🇨🇨 "
                          + "🇨🇩 🇨🇫 🇨🇬 🇨🇭 🇨🇮 🇨🇰 🇨🇱 🇨🇲 🇨🇳 🇨🇴 "
                          + "🇨🇷 🇨🇺 🇨🇻 🇨🇼 🇨🇽 🇨🇾 🇨🇿 🇩🇪 🇩🇯 🇩🇰 "
                          + "🇩🇲 🇩🇴 🇩🇿 🇪🇨 🇪🇪 🇪🇬 🇪🇭 🇪🇷 🇪🇸 🇪🇹 "
                          + "🇫🇮 🇫🇯 🇫🇰 🇫🇲 🇫🇴 🇫🇷 🇬🇦 🇬🇧 🇬🇩 🇬🇪 "
                          + "🇬🇭 🇬🇮 🇬🇱 🇬🇲 🇬🇳 🇬🇵 🇬🇶 🇬🇷 🇬🇸 🇬🇹 "
                          + "🇬🇺 🇬🇼 🇬🇾 🇭🇰 🇭🇳 🇭🇷 🇭🇹 🇭🇺 🇮🇩 🇮🇪 "
                          + "🇮🇱 🇮🇳 🇮🇶 🇮🇷 🇮🇸 🇮🇹 🇯🇲 🇯🇴 🇯🇵 🇰🇪 "
                          + "🇰🇬 🇰🇭 🇰🇮 🇰🇲 🇰🇳 🇰🇷 🇰🇼 🇰🇾 🇰🇿 🇱🇦 "
                          + "🇱🇧 🇱🇨 🇱🇮 🇱🇰 🇱🇷 🇱🇸 🇱🇹 🇱🇺 🇱🇻 🇱🇾 "
                          + "🇲🇦 🇲🇨 🇲🇩 🇲🇪 🇲🇬 🇲🇭 🇲🇰 🇲🇱 🇲🇲 🇲🇳 "
                          + "🇲🇴 🇲🇵 🇲🇷 🇲🇸 🇲🇹 🇲🇺 🇲🇻 🇲🇼 🇲🇽 🇲🇾 "
                          + "🇲🇿 🇳🇦 🇳🇨 🇳🇪 🇳🇬 🇳🇮 🇳🇱 🇳🇴 🇳🇵 🇳🇷 "
                          + "🇳🇺 🇳🇿 🇴🇲 🇵🇦 🇵🇪 🇵🇫 🇵🇬 🇵🇭 🇵🇰 🇵🇱 "
                          + "🇵🇷 🇵🇸 🇵🇹 🇵🇼 🇵🇾 🇶🇦 🇷🇴 🇷🇸 🇷🇺 🇷🇼 "
                          + "🇸🇦 🇸🇧 🇸🇨 🇸🇩 🇸🇪 🇸🇬 🇸🇮 🇸🇰 🇸🇱 🇸🇲 "
                          + "🇸🇳 🇸🇴 🇸🇷 🇸🇸 🇸🇹 🇸🇻 🇸🇽 🇸🇾 🇸🇿 🇹🇭 "
                          + "🇹🇯 🇹🇱 🇹🇲 🇹🇳 🇹🇴 🇹🇷 🇹🇹 🇹🇻 🇹🇼 🇹🇿 "
                          + "🇺🇦 🇺🇬 🇺🇳 🇺🇸 🇺🇾 🇺🇿 🇻🇦 🇻🇨 🇻🇪 🇻🇳 "
                          + "🇻🇺 🇼🇸 🇽🇰 🇾🇪 🇿🇦 🇿🇲 🇿🇼"
                    ));

                    ArrayList<List<String>> immutable = new ArrayList<>();
                    for (List<String> category : data) {
                        immutable.add(Collections.unmodifiableList(category));
                    }

                    categories = Collections.unmodifiableList(immutable);
                    ready = true;
                }
            });
        }
    }

    private static List<String> makeList(String source) {
        if (source == null || source.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String[] values = source.trim().split("\\s+");
        ArrayList<String> result = new ArrayList<>(values.length);

        for (String value : values) {
            if (!value.isEmpty() && !result.contains(value)) {
                result.add(value);
            }
        }

        return result;
    }

    static boolean isReady() {
        return ready;
    }

    static List<String> get(int category) {
        if (!ready || category < 0 || category >= categories.size()) {
            return Collections.emptyList();
        }
        return categories.get(category);
    }
}
