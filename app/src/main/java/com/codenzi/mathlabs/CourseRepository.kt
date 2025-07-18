package com.codenzi.mathlabs

import android.content.Context
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uygulamanın veri işlemlerini merkezileştiren sınıf.
 * Bu sınıf, Hilt tarafından yönetilen bir Singleton'dır, yani uygulama boyunca tek bir örneği oluşturulur.
 * UI katmanı (ViewModel), verinin nereden veya nasıl geldiğini bilmez, sadece bu sınıftan talep eder.
 */
@Singleton
class CourseRepository @Inject constructor() {

    /**
     * Verilen bir string'i, dosya yolu için uygun hale getirir.
     * Türkçe karakterleri İngilizce karşılıkları ile değiştirir,
     * boşlukları alt çizgi (_) yapar ve tüm harfleri küçültür.
     */
    private fun normalizeAndFormatForAssetName(input: String): String {
        var normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
        normalized = normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        normalized = normalized
            .replace("ı", "i")
            .replace("İ", "I")
            .replace("ğ", "g")
            .replace("Ğ", "G")
            .replace("ü", "u")
            .replace("Ü", "U")
            .replace("ş", "s")
            .replace("Ş", "S")
            .replace("ö", "o")
            .replace("Ö", "O")
            .replace("ç", "c")
            .replace("Ç", "C")
        return normalized.lowercase(Locale.ROOT).replace(" ", "_")
    }

    /**
     * Tüm dersleri ve onlara ait konuları bir liste olarak döndürür.
     * Her bir konu için ilgili PDF'in Firebase Storage'daki yolunu, seçili olan dile göre oluşturur.
     */
    fun getCourses(context: Context): List<Course> {
        // SharedPreferences'dan o an seçili olan dili al.
        // Eğer dil seçilmemişse veya null ise, varsayılan olarak "tr" kullan.
        val languageCode = SharedPreferencesManager.getLanguage(context) ?: "tr"

        // Kod tekrarını önlemek için ders ve konu listelerini oluşturan bir yardımcı fonksiyon.
        val createCourseWithTopics: (courseResId: Int, topicResIds: List<Int>) -> Course = { courseResId, topicResIds ->
            val courseTitle = context.getString(courseResId)
            val coursePath = normalizeAndFormatForAssetName(courseTitle)

            val topics = topicResIds.map { topicResId ->
                val topicTitle = context.getString(topicResId)
                val topicPath = normalizeAndFormatForAssetName(topicTitle)

                // Dosya yolunun başına dil kodunu ekleyerek tam yolu oluştur.
                // Örnek: "tr/kalkulus/limit_ve_sureklilik.pdf"
                // Örnek: "en/calculus/limits_and_continuity.pdf"
                val pdfPathInFirebase = "$languageCode/$coursePath/$topicPath.pdf"

                Topic(
                    title = topicTitle,
                    pdfAssetName = pdfPathInFirebase, // Bu alan artık dil bazlı Firebase yolunu tutuyor
                    hasPdf = true // PDF'lerin Firebase'de var olduğunu varsayıyoruz
                )
            }.toMutableList()

            // "Genel Soru Çözümü" konusunu ekle
            val generalProblemSolvingTitle = context.getString(R.string.topic_general_problem_solving)
            val generalProblemSolvingPath = normalizeAndFormatForAssetName(generalProblemSolvingTitle)
            val generalProblemSolvingPdfPath = "$languageCode/$coursePath/$generalProblemSolvingPath.pdf"
            topics.add(Topic(
                title = generalProblemSolvingTitle,
                pdfAssetName = generalProblemSolvingPdfPath,
                hasPdf = true
            ))


            Course(title = courseTitle, topics = topics)
        }

        // Tüm dersleri ve konularını R.string kaynaklarını kullanarak oluştur.
        return listOf(
            createCourseWithTopics(R.string.course_calculus, listOf(
                R.string.topic_calculus_limit, R.string.topic_calculus_derivative_rules, R.string.topic_calculus_derivative_apps,
                R.string.topic_calculus_mean_value, R.string.topic_calculus_indefinite_integral, R.string.topic_calculus_definite_integral,
                R.string.topic_calculus_sequences_series, R.string.topic_calculus_power_series, R.string.topic_calculus_multivariable,
                R.string.topic_calculus_partial_derivatives, R.string.topic_calculus_multiple_integrals, R.string.topic_calculus_vector_analysis,
                R.string.topic_calculus_fourier, R.string.topic_calculus_metric_spaces
            )),
            createCourseWithTopics(R.string.course_complex_analysis, listOf(
                R.string.topic_complex_numbers, R.string.topic_complex_analytic_functions, R.string.topic_complex_cauchy_riemann,
                R.string.topic_complex_integration, R.string.topic_complex_cauchy_integral, R.string.topic_complex_taylor_laurent,
                R.string.topic_complex_residue, R.string.topic_complex_conformal, R.string.topic_complex_dynamic_systems
            )),
            createCourseWithTopics(R.string.course_numerical_analysis, listOf(
                R.string.topic_numerical_error_analysis, R.string.topic_numerical_root_finding, R.string.topic_numerical_linear_systems,
                R.string.topic_numerical_iterative_methods, R.string.topic_numerical_interpolation, R.string.topic_numerical_least_squares,
                R.string.topic_numerical_differentiation_integration, R.string.topic_numerical_matrix_decomposition, R.string.topic_numerical_optimization
            )),
            createCourseWithTopics(R.string.course_linear_algebra, listOf(
                R.string.topic_linear_matrices, R.string.topic_linear_systems, R.string.topic_linear_vector_spaces,
                R.string.topic_linear_independence, R.string.topic_linear_transformations, R.string.topic_linear_kernel_image,
                R.string.topic_linear_eigenvalues, R.string.topic_linear_diagonalization, R.string.topic_linear_inner_product,
                R.string.topic_linear_tensors, R.string.topic_linear_numerical
            )),
            createCourseWithTopics(R.string.course_abstract_math, listOf(
                R.string.topic_abstract_logic, R.string.topic_abstract_set_theory, R.string.topic_abstract_proof_methods,
                R.string.topic_abstract_relations, R.string.topic_abstract_functions, R.string.topic_abstract_cardinality,
                R.string.topic_abstract_category_theory, R.string.topic_abstract_model_theory
            )),
            createCourseWithTopics(R.string.course_algebra, listOf(
                R.string.topic_algebra_groups, R.string.topic_algebra_cyclic_groups, R.string.topic_algebra_lagrange,
                R.string.topic_algebra_normal_subgroups, R.string.topic_algebra_homomorphisms, R.string.topic_algebra_rings_fields,
                R.string.topic_algebra_module_theory
            )),
            createCourseWithTopics(R.string.course_number_theory, listOf(
                R.string.topic_number_divisibility, R.string.topic_number_prime_numbers, R.string.topic_number_modular_arithmetic,
                R.string.topic_number_chinese_remainder, R.string.topic_number_fermat_euler,
                R.string.topic_number_diophantine, R.string.topic_number_cryptography, R.string.topic_number_quadratic_residues,
                R.string.topic_number_analytic_number_theory
            )),
            createCourseWithTopics(R.string.course_differential_equations, listOf(
                R.string.topic_diffeq_first_order, R.string.topic_diffeq_higher_order, R.string.topic_diffeq_undetermined_coefficients,
                R.string.topic_diffeq_variation_parameters, R.string.topic_diffeq_laplace, R.string.topic_diffeq_initial_value,
                R.string.topic_diffeq_systems, R.string.topic_diffeq_chaotic_systems, R.string.topic_diffeq_numerical_solutions
            )),
            createCourseWithTopics(R.string.course_pde, listOf(
                R.string.topic_pde_concepts, R.string.topic_pde_fourier, R.string.topic_pde_separation_variables,
                R.string.topic_pde_heat_equation, R.string.topic_pde_wave_equation, R.string.topic_pde_laplace_equation,
                R.string.topic_pde_green_functions, R.string.topic_pde_finite_element
            )),
            createCourseWithTopics(R.string.course_analytic_geometry, listOf(
                R.string.topic_analytic_geometry_coordinates, R.string.topic_analytic_geometry_line_plane, R.string.topic_analytic_geometry_conic_sections,
                R.string.topic_analytic_geometry_quadric_surfaces, R.string.topic_analytic_geometry_transformations, R.string.topic_analytic_geometry_projective,
                R.string.topic_analytic_geometry_vector_fields
            )),
            createCourseWithTopics(R.string.course_differential_geometry, listOf(
                R.string.topic_diffgeo_curves, R.string.topic_diffgeo_frenet_serret, R.string.topic_diffgeo_surfaces,
                R.string.topic_diffgeo_fundamental_forms, R.string.topic_diffgeo_gaussian_curvature, R.string.topic_diffgeo_gauss_bonnet,
                R.string.topic_diffgeo_riemann_geometry, R.string.topic_diffgeo_manifolds
            )),
            createCourseWithTopics(R.string.course_topology, listOf(
                R.string.topic_topology_spaces, R.string.topic_topology_metric_spaces, R.string.topic_topology_continuity,
                R.string.topic_topology_connectedness, R.string.topic_topology_compactness, R.string.topic_topology_fundamental_group,
                R.string.topic_topology_knot_theory, R.string.topic_topology_homology_cohomology
            )),
            createCourseWithTopics(R.string.course_probability, listOf(
                R.string.topic_probability_counting, R.string.topic_probability_conditional, R.string.topic_probability_random_variables,
                R.string.topic_probability_discrete_distributions, R.string.topic_probability_continuous_distributions, R.string.topic_probability_expected_value,
                R.string.topic_probability_limit_theorems, R.string.topic_probability_markov_chains, R.string.topic_probability_statistical_inference
            )),
            createCourseWithTopics(R.string.course_functional_analysis, listOf(
                R.string.topic_functional_normed_spaces, R.string.topic_functional_hilbert_spaces, R.string.topic_functional_linear_operators,
                R.string.topic_functional_spectral_theory, R.string.topic_functional_weak_topologies, R.string.topic_functional_applications
            ))
        )
    }
}