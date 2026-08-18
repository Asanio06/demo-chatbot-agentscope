package io.agentscope.demo.back.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Routage Jackson 2 pour le contrôleur AG-UI sous Spring Boot 4.
 *
 * <p><b>Problème.</b> agentscope-java 2.0.2 est bâti contre Jackson 2 : son modèle AG-UI
 * ({@code RunAgentInput}, {@code MessageContent}…) est annoté avec les annotations
 * <b>Jackson 2</b> ({@code com.fasterxml.jackson.databind.annotation.JsonDeserialize}).
 * Spring Boot 4 s'appuie par défaut sur <b>Jackson 3</b> ({@code tools.jackson}), qui ne lit pas
 * ces annotations : dès qu'un message AG-UI porte un {@code content}, la désérialisation du
 * {@code @RequestBody RunAgentInput} échoue (500 « no Creators for MessageContent »).</p>
 *
 * <p><b>Solution.</b> Le module officiel {@code spring-boot-jackson2} (déclaré dans le pom) fournit
 * l'infra Jackson 2 (ObjectMapper, {@link Jackson2ObjectMapperBuilder}) mais ne bascule pas
 * <em>wire-level</em> le converter MVC (Spring Boot 4 garde Jackson 3 par défaut). Ce config
 * ajoute donc un {@link MappingJackson2HttpMessageConverter} Jackson 2 <em>en tête</em> de la liste
 * des converters HTTP, <b>sélectif</b> : il ne répond que pour les types du package
 * {@code io.agentscope.core.agui.model} (les DTO AG-UI). Tout le reste de l'app continue de passer
 * par Jackson 3, préservant le comportement Spring Boot 4 par défaut.</p>
 */
@Configuration
public class AguiJackson2ConverterConfig implements WebMvcConfigurer {

    /** Package des modèles AG-UI d'agentscope, à router vers Jackson 2. */
    private static final String AGUI_MODEL_PACKAGE = "io.agentscope.core.agui.model";

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(0, aguiJackson2Converter());
    }

    /**
     * Un converter Jackson 2 construit via le builder officiel Spring MVC
     * ({@link Jackson2ObjectMapperBuilder}), dont {@code canRead}/{@code canWrite} ne répondent
     * que pour les types AG-UI. Exposé en {@code @Bean} pour être testable (voir
     * {@code ChatbotApplicationIT.aguiRunInputWithMessageContentDeserializes}). Mis en tête de
     * liste, il capte seul les payloads AG-UI.
     */
    @Bean
    public MappingJackson2HttpMessageConverter aguiJackson2Converter() {
        ObjectMapper jackson2 = Jackson2ObjectMapperBuilder.json().build();
        return new MappingJackson2HttpMessageConverter(jackson2) {
            @Override
            public boolean canRead(Class<?> clazz, MediaType mediaType) {
                return isAguiModel(clazz) && super.canRead(clazz, mediaType);
            }

            @Override
            public boolean canWrite(Class<?> clazz, MediaType mediaType) {
                return isAguiModel(clazz) && super.canWrite(clazz, mediaType);
            }
        };
    }

    private static boolean isAguiModel(Class<?> clazz) {
        return clazz != null
            && clazz.getName().startsWith(AGUI_MODEL_PACKAGE + ".");
    }
}
