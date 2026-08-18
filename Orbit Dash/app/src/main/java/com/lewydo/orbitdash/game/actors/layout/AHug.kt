package com.lewydo.orbitdash.game.actors.layout

import com.badlogic.gdx.scenes.scene2d.Actor
import com.lewydo.orbitdash.game.utils.advanced.AdvancedGroup
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen

// ═════════════════════════════════════════════════════════════════════════════
//  AHug — батько розміром з дитину (wrap content).
//
//  Дзеркалить розмір вмісту після кожного act (HUG вмісту на той момент уже
//  порахований), сам вмісту НІЧОГО не пише: ні позиції, ні розміру. Тому:
//
//    • для AConstraintLayout це звичайна HUG-дитина — центрується, тягнеться
//      констрейнтами, реагує на зміну розміру вмісту (лаг 1 кадр,
//      самовиправляється — та сама механіка, що в AAnchorOf);
//    • content.x/y ВІЛЬНІ — їх можна анімувати ззовні, і лейаут цю анімацію
//      не переб'є, бо пише координати ОБГОРТКИ, а не вмісту. Це емуляція
//      CSS transform: translate, якого в scene2d не існує: там x/y — це
//      і є layout, тож пряма анімація позиції воює з констрейнтами
//      (звідси були detach → політ → re-attach і правила порядку).
//
//  Сама обгортка анімацій не має і не повинна мати: рух — справа екрана,
//  який знає, ЩО і ЯК має з'являтись. Тут лише геометрія.
//
//  Обгортка не кліпить — вміст під час анімацій може малюватись поза її
//  межами, це нормально. Вміст один: композиції збирати всередині вмісту.
// ═════════════════════════════════════════════════════════════════════════════
class AHug(
    override val screen: AdvancedScreen,
    val content: Actor,
) : AdvancedGroup() {

    init {
        // Сід розміру ЩЕ В КОНСТРУКТОРІ, не в addActorsOnGroup: той хук
        // спрацьовує лише при потраплянні на stage — а AConstraintLayout.add()
        // перевіряє розмір (require) ДО того, як покладе актора на сцену.
        // Вміст на цей момент уже несе свій сід: setSize ставиться перед
        // обгортанням (…apply { setSize(300f, 1f) }).
        syncSize()
    }

    override fun addActorsOnGroup() {
        addActor(content)
        content.setPosition(0f, 0f)
    }

    override fun act(delta: Float) {
        super.act(delta)   // спершу діти: HUG вмісту рахується в їхньому act
        syncSize()
    }

    private fun syncSize() {
        if (width != content.width || height != content.height) {
            setSize(content.width, content.height)
        }
    }

}
